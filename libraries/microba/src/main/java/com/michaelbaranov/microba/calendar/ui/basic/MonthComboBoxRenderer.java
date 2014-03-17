package com.michaelbaranov.microba.calendar.ui.basic;

import java.awt.Component;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JList;

class MonthComboBoxRenderer extends DefaultListCellRenderer {

	// private Locale locale;

	private TimeZone zone;

	private SimpleDateFormat dateFormat;

	public MonthComboBoxRenderer(Locale locale, TimeZone zone) {
		// this.locale = locale;
		this.zone = zone;
		dateFormat = new SimpleDateFormat("MMMM", locale);
		dateFormat.setTimeZone(zone);
	}

	public Component getListCellRendererComponent(JList list, Object value,
			int index, boolean isSelected, boolean cellHasFocus) {
		super.getListCellRendererComponent(list, value, index, isSelected,
				cellHasFocus);

		Date date = (Date) value;
		setText(dateFormat.format(date));

		return this;
	}

	public void setLocale(Locale locale) {
		// this.locale = locale;
		dateFormat = new SimpleDateFormat("MMMM", locale);
		dateFormat.setTimeZone(zone);
	}

	public void setZone(TimeZone zone) {
		this.zone = zone;
		dateFormat.setTimeZone(zone);
	}

	// public Locale getLocale() {
	// return locale;
	// }
	//
	// public TimeZone getZone() {
	// return zone;
	// }

}
