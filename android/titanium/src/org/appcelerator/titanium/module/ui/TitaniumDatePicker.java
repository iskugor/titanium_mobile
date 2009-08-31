package org.appcelerator.titanium.module.ui;

import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import org.appcelerator.titanium.TitaniumModuleManager;
import org.appcelerator.titanium.api.ITitaniumDatePicker;
import org.appcelerator.titanium.config.TitaniumConfig;
import org.appcelerator.titanium.util.Log;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Context;
import android.os.Bundle;
import android.os.Message;
import android.text.format.DateFormat;
import android.widget.DatePicker;
import android.widget.TimePicker;

public class TitaniumDatePicker extends TitaniumBaseNativeControl
	implements ITitaniumDatePicker,
		DatePicker.OnDateChangedListener, TimePicker.OnTimeChangedListener
{
	private static final String LCAT = "TiDatePicker";
	private static final boolean DBG = TitaniumConfig.LOGD;

	private static final long DAY_IN_MILLIS = 24 * 60 * 60 * 1000;

	private static final String CHANGE_EVENT = "change";

	private static final int MSG_DATE_CHANGE = 300;
	private static final int MSG_TIME_CHANGE = 301;
	private static final int MSG_SET_VALUE = 302;

	private Long value;
	private Long lastValue;
	private Long minDate;
	private Long maxDate;
	private int mode;
	private int minuteInterval;
	private TimeZone timeZone;
	private boolean midnightCrossing; // Used in MODE_TIME and min/max tests

	public TitaniumDatePicker(TitaniumModuleManager tmm)
	{
		super(tmm);
		eventManager.supportEvent(CHANGE_EVENT);
		this.mode = MODE_DATE;
		this.minuteInterval = 1;
		//this.timeZone = TimeZone.getTimeZone("UTC");
		this.timeZone = TimeZone.getDefault();
		this.value = getCalendar().getTimeInMillis();
		this.midnightCrossing = false;
	}

	private Calendar getCalendar() {
		return GregorianCalendar.getInstance(timeZone);
	}

	@Override
	protected void setLocalOptions(JSONObject o) throws JSONException {
		super.setLocalOptions(o);

		if (o.has("value")) {
			this.value = o.getLong("value");
		}
		if (o.has("mode")) {
			this.mode = o.getInt("mode");
		}
		if (o.has("minDate")) {
			this.minDate = o.getLong("minDate");
		}
		if (o.has("maxDate")) {
			this.maxDate = o.getLong("maxDate");
		}
		if (o.has("minuteInterval")) {
			int mi = o.getInt("minuteInterval");
			if (mi >= 1 && mi <= 30 && mi % 60 == 0) {
				this.minuteInterval = mi;
			}
		}

		//iPhone ignores both values if max <= min
		if (minDate != null && maxDate != null) {
			if (maxDate < minDate) {
				Log.w(LCAT, "maxDate is less or equal minDate, ignoring both settings.");
				minDate = null;
				maxDate = null;
			}
		}

		//Characterize the date range if in TIME mode
		if (mode == MODE_TIME && minDate != null && maxDate != null) {
			if (maxDate.longValue() - minDate.longValue() > DAY_IN_MILLIS) {
				Log.w(LCAT, "maxDate - minDate > 1 day, ignoring in MODE_TIME");
				minDate = null;
				maxDate = null;
			} else {
				Calendar mind = getCalendar();
				mind.setTimeInMillis(minDate);

				Calendar maxd = getCalendar();
				maxd.setTimeInMillis(maxDate);

				if (mind.get(Calendar.DAY_OF_MONTH) != maxd.get(Calendar.DAY_OF_MONTH)) {
					Log.i(LCAT, "Time range crosses midnight");
					midnightCrossing = true;
				}
			}
		}
	}

	@Override
	public void createControl(TitaniumModuleManager tmm)
	{
		Context context = tmm.getAppContext();
		Calendar cal = getCalendar();

		if (minDate != null) {
			if (value.longValue() < minDate.longValue()) {
				value = minDate.longValue();
			}
		}
		if (maxDate != null) {
			if (value.longValue() > maxDate.longValue()) {
				value = maxDate.longValue();
			}
		}

		if (mode == MODE_DATE) {
			DatePicker dp = new DatePicker(context);
			cal.setTimeInMillis(value);
			dp.init(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH), this);
			control = dp;
		} else if (mode == MODE_TIME) {
			TimePicker tp = new TimePicker(context);
			cal.setTimeInMillis(value);
			tp.setCurrentHour(cal.get(Calendar.HOUR_OF_DAY));
			tp.setCurrentMinute(cal.get(Calendar.MINUTE));
			tp.setOnTimeChangedListener(this);
			control = tp;
		} else {
			Log.w(LCAT, "Mode not supported: " + mode);
		}
	}

	public void onDateChanged(DatePicker view, int year, int monthOfYear, int dayOfMonth)
	{
		Message m = handler.obtainMessage(MSG_DATE_CHANGE);
		Bundle b = m.getData();
		b.putInt("YEAR", year);
		b.putInt("MONTH", monthOfYear);
		b.putInt("DAY", dayOfMonth);
		m.sendToTarget();
	}

	public void onTimeChanged(TimePicker view, int hourOfDay, int minute)
	{
		Message m = handler.obtainMessage(MSG_DATE_CHANGE);
		Bundle b = m.getData();
		b.putInt("HOUR", hourOfDay);
		b.putInt("MINUTE", minute);
		m.sendToTarget();
	}

	@Override
	public boolean handleMessage(Message msg)
	{
		boolean handled = false;

		if (msg.what == MSG_DATE_CHANGE || msg.what == MSG_TIME_CHANGE) {
			if (DBG) {
				Log.d(LCAT, "CHANGE Message");
			}
			doChange(value, msg.getData());
			handled = true;
		} else if (msg.what == MSG_SET_VALUE) {
			if (DBG) {
				Log.d(LCAT, "SET_VALUE Message");
			}
			Calendar cal = getCalendar();
			cal.setTimeInMillis(msg.getData().getLong("VALUE"));

			if (mode == MODE_DATE) {
				DatePicker dp = (DatePicker) control;
				dp.updateDate(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH));
			} else if (mode == MODE_TIME) {
				TimePicker tp = (TimePicker) control;

				tp.setCurrentHour(cal.get(Calendar.HOUR_OF_DAY));
				tp.setCurrentMinute(cal.get(Calendar.MINUTE));
			} else {
				Log.w(LCAT, "setValue not supported in mode: " + mode);
			}

			handled = true;
		} else {
			handled = super.handleMessage(msg);
		}
		return handled;
	}

	private void doChange(long v, Bundle b)
	{
		Calendar cal = getCalendar();
		boolean needsAdjustment = toDate(cal, v, b);
		value = cal.getTimeInMillis();

		if (lastValue != null && lastValue.longValue() == value.longValue()) {
			return;
		}

		if (DBG) {
			Calendar t = getCalendar();

			StringBuilder sb = new StringBuilder();
			sb.append("Min: ");
			if (minDate != null) {
				t.setTimeInMillis(minDate);
				sb.append(DateFormat.format("MM/dd/yy hh:mm", t));
			} else {
				sb.append("not set");
			}
			sb.append(" value: ");
			sb.append(DateFormat.format("MM/dd/yy hh:mm", cal));
			sb.append(" maxDate: ");
			if (maxDate != null) {
				t.setTimeInMillis(maxDate);
				sb.append(DateFormat.format("MM/dd/yy hh:mm", t));
			} else {
				sb.append("not set");
			}
			t = null;
			Log.d(LCAT, sb.toString());
		}

		// using simple check for MODE_TIME && midnightCrossing, since it gets set only if MODE_TIME
		if (!midnightCrossing) {
			if (minDate != null) {
				if (value.longValue() < minDate.longValue()) {
					value = minDate.longValue();
					needsAdjustment = true;
				}
			}
			if (maxDate != null) {
				if(value.longValue() > maxDate.longValue()) {
					value = maxDate.longValue();
					needsAdjustment = true;
				}
			}
		} else {
			//minDate && maxDate had to be non-null to get here.
			if (value.longValue() < minDate.longValue()) {
				Calendar maxd = getCalendar();
				maxd.setTimeInMillis(maxDate);
				maxd.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY));
				maxd.set(Calendar.MINUTE, cal.get(Calendar.MINUTE));
				maxd.set(Calendar.AM_PM, Calendar.AM);

				if (value.longValue() < maxd.getTimeInMillis()) {
					value = maxd.getTimeInMillis();
				} else {
					value = minDate.longValue();
				}
				needsAdjustment = true;
			} else if (value.longValue() > maxDate.longValue()) {
				Calendar mind = getCalendar();
				mind.setTimeInMillis(minDate);
				mind.set(Calendar.HOUR_OF_DAY, cal.get(Calendar.HOUR_OF_DAY));
				mind.set(Calendar.MINUTE, cal.get(Calendar.MINUTE));
				mind.set(Calendar.AM_PM, Calendar.PM);

				if (value > mind.getTimeInMillis()) {
					value = mind.getTimeInMillis();
				} else {
					value = maxDate.longValue();
				}
				needsAdjustment = true;
			}

			// Sanity check
			if (needsAdjustment) {
				if (minDate != null) {
					if (value.longValue() < minDate.longValue()) {
						value = minDate.longValue();
					}
				}
				if (maxDate != null) {
					if(value.longValue() > maxDate.longValue()) {
						value = maxDate.longValue();
					}
				}
			}
		}

		if (lastValue == null  || needsAdjustment || (lastValue.longValue() != value.longValue())) {
			if (DBG) {
				Log.d(LCAT, "Sending date changed");
			}
			lastValue = value;
			if (needsAdjustment) {
				setValue(value, null);
			}

			try {
				JSONObject o = new JSONObject();
				o.put("value", value);
				eventManager.invokeSuccessListeners(CHANGE_EVENT, o.toString());
			} catch (JSONException e) {
				Log.e(LCAT, "Error constructing change event: ", e);
			}
		}
	}

	private boolean toDate(Calendar cal, long v, Bundle b)
	{
		boolean dayAdjusted = false;

		cal.setTimeInMillis(v);

		int orgDayOfMonth = cal.get(Calendar.DAY_OF_MONTH);
		cal.set(Calendar.DAY_OF_MONTH, 1); // Temporarily set date to 1 to get the real date

		setField(cal, Calendar.YEAR, "YEAR", b);
		setField(cal, Calendar.MONTH, "MONTH", b);

		int dayOfMonth =  b.getInt("DAY", -1);
		int max = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
		if (dayOfMonth > max) {
			cal.set(Calendar.DAY_OF_MONTH, max);
			dayAdjusted = true;
		} else {
			if (dayOfMonth != -1) {
				setField(cal, Calendar.DAY_OF_MONTH, "DAY", b);
			} else {
				cal.set(Calendar.DAY_OF_MONTH, orgDayOfMonth);
			}
		}
		setField(cal, Calendar.HOUR_OF_DAY, "HOUR", b);
		setField(cal, Calendar.MINUTE, "MINUTE", b);

		return dayAdjusted;
	}

	private void setField(Calendar cal, int field, String key, Bundle b) {
		int v = b.getInt(key, -1);
		if (DBG) {
			Log.d(LCAT, key + ": " + v);
		}
		if (v != -1) {
			cal.set(field, v);
		}
	}

	public void setValue(long date, String options) {
		Message m = handler.obtainMessage(MSG_SET_VALUE);
		m.getData().putLong("VALUE", date);
		m.sendToTarget();
	}
}
