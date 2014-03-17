package com.michaelbaranov.microba.calendar.ui.basic;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.swing.SpinnerNumberModel;

class YearSpinnerModel extends SpinnerNumberModel {

	public static final String PROPERTY_NAME_LOCALE = "locale";

	public static final String PROPERTY_NAME_DATE = "date";

	public static final String PROPERTY_NAME_ZONE = "zone";

	private PropertyChangeSupport changeSupport = new PropertyChangeSupport(
			this);

	private Locale locale;

	private TimeZone zone;

	private Calendar calendar;

	public YearSpinnerModel(Date date, Locale locale, TimeZone zone) {
		this.locale = locale;
		this.zone = zone;
		createLocaleAndZoneSensitive();
		calendar.setTime(date);
	}

	private void createLocaleAndZoneSensitive() {
		if (calendar != null) {
			Date old = calendar.getTime();
			calendar = Calendar.getInstance(zone, locale);
			calendar.setTime(old);
		} else
			calendar = Calendar.getInstance(zone, locale);
	}

	public Object getValue() {
		return new Integer(calendar.get(Calendar.YEAR));
	}

	public void setValue(Object value) {
		Number newVal = (Number) value;
		Number oldVal = (Number) getValue();
		if (oldVal.longValue() != newVal.longValue()) {

			int diff = newVal.intValue() - oldVal.intValue();
			int sign = diff > 0 ? 1 : -1;
			if (diff < 0)
				diff = -diff;
			Date oldDate = calendar.getTime();

			for (int i = 0; i < diff; i++)
				calendar.add(Calendar.YEAR, sign);

			changeSupport.firePropertyChange(PROPERTY_NAME_DATE, oldDate,
					getDate());
			fireStateChanged();
		}
	}

	public Object getNextValue() {

		Integer currVal = (Integer) getValue();
		int newVal = currVal.intValue() + 1;

		if (newVal <= calendar.getActualMaximum(Calendar.YEAR))
			return new Integer(newVal);

		return currVal;
	}

	public Object getPreviousValue() {
		Integer currVal = (Integer) getValue();
		int newVal = currVal.intValue() - 1;

		if (newVal >= calendar.getActualMinimum(Calendar.YEAR))
			return new Integer(newVal);

		return currVal;
	}

	public Date getDate() {
		return calendar.getTime();
	}

	public void setDate(Date date) {
		Date old = calendar.getTime();
		if (!old.equals(date)) {
			calendar.setTime(date);
			changeSupport.firePropertyChange(PROPERTY_NAME_DATE, old, date);
			fireStateChanged();
		}
	}

	public Locale getLocale() {
		return locale;
	}

	public void setLocale(Locale locale) {
		Locale old = this.locale;
		this.locale = locale;
		createLocaleAndZoneSensitive();
		changeSupport.firePropertyChange(PROPERTY_NAME_LOCALE, old, locale);
		fireStateChanged();
	}

	public TimeZone getZone() {
		return zone;
	}

	public void setZone(TimeZone zone) {
		TimeZone old = this.zone;
		this.zone = zone;
		createLocaleAndZoneSensitive();
		changeSupport.firePropertyChange(PROPERTY_NAME_LOCALE, old, locale);
		fireStateChanged();
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		changeSupport.addPropertyChangeListener(listener);
	}

	public void addPropertyChangeListener(String propertyName,
			PropertyChangeListener listener) {
		changeSupport.addPropertyChangeListener(propertyName, listener);
	}

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		changeSupport.removePropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(String propertyName,
			PropertyChangeListener listener) {
		changeSupport.removePropertyChangeListener(propertyName, listener);
	}

	public PropertyChangeListener[] getPropertyChangeListeners() {
		return changeSupport.getPropertyChangeListeners();
	}

	public PropertyChangeListener[] getPropertyChangeListeners(
			String propertyName) {
		return changeSupport.getPropertyChangeListeners(propertyName);
	}

	public boolean hasListeners(String propertyName) {
		return changeSupport.hasListeners(propertyName);
	}

}
