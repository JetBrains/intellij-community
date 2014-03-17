package com.michaelbaranov.microba.calendar.ui.basic;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;

class MonthComboBoxModel extends AbstractListModel implements
		ComboBoxModel {

	public static final String PROPERTY_NAME_LOCALE = "locale";

	public static final String PROPERTY_NAME_DATE = "date";

	private PropertyChangeSupport changeSupport = new PropertyChangeSupport(
			this);

	private Calendar calendar;

	private Locale locale;

	private TimeZone zone;

	public MonthComboBoxModel(Date date, Locale locale, TimeZone zone) {
		super();
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

	public Locale getLocale() {
		return locale;
	}

	public void setLocale(Locale locale) {
		Locale old = this.locale;
		this.locale = locale;
		createLocaleAndZoneSensitive();
		changeSupport.firePropertyChange(PROPERTY_NAME_LOCALE, old, locale);
		fireContentsChanged(this, 0, getSize() - 1);
	}

	public Date getDate() {
		return calendar.getTime();
	}

	public void setDate(Date date) {
		Date old = getDate();
		calendar.setTime(date);
		changeSupport.firePropertyChange(PROPERTY_NAME_DATE, old, date);
		fireContentsChanged(this, 0, getSize() - 1);
	}

	public void setSelectedItem(Object anItem) {
		Date aDate = (Date) anItem;
		setDate(aDate);
	}

	public Object getSelectedItem() {
		return calendar.getTime();
	}

	public int getSize() {
		return calendar.getActualMaximum(Calendar.MONTH) + 1;
	}

	public Object getElementAt(int index) {
		Calendar c = Calendar.getInstance(locale);
		c.setTime(calendar.getTime());

		c.set(Calendar.MONTH, 0);
		for (int i = 0; i < index; i++)
			c.add(Calendar.MONTH, 1);

		return c.getTime();
	}

	public TimeZone getZone() {
		return zone;
	}

	public void setZone(TimeZone zone) {
		this.zone = zone;
		createLocaleAndZoneSensitive();
		fireContentsChanged(this, 0, getSize() - 1);
	}

	public void addPropertyChangeListener(PropertyChangeListener listener) {
		changeSupport.addPropertyChangeListener(listener);
	}

	public void addPropertyChangeListener(String propertyName,
			PropertyChangeListener listener) {
		changeSupport.addPropertyChangeListener(propertyName, listener);
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

	public void removePropertyChangeListener(PropertyChangeListener listener) {
		changeSupport.removePropertyChangeListener(listener);
	}

	public void removePropertyChangeListener(String propertyName,
			PropertyChangeListener listener) {
		changeSupport.removePropertyChangeListener(propertyName, listener);
	}

}
