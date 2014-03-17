package com.michaelbaranov.microba.calendar.ui.basic;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.GridLayout;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

public class CalendarNumberOfWeekPanel extends JPanel /*
														 * implements
														 * PropertyChangeListener
														 */{

	public static final String PROPERTY_NAME_BASE_DATE = "baseDate";

	public static final String PROPERTY_NAME_LOCALE = "locale";

	public static final String PROPERTY_NAME_ZONE = "zone";

	private Color backgroundColorActive = UIManager.getColor("activeCaption");

	private Color backgroundColorInactive = UIManager
			.getColor("inactiveCaption");

	private Date baseDate;

	private Locale locale;

	private TimeZone zone;

	private JLabel[] labels = new JLabel[6];

	public CalendarNumberOfWeekPanel(Date baseDate, Locale locale,
			TimeZone timeZone) {
		super();
		this.baseDate = baseDate == null ? new Date() : baseDate;
		this.locale = locale;
		this.zone = timeZone;

		setLayout(new GridLayout(6, 1, 2, 2));

		for (int i = 0; i < 6; i++) {
			JLabel l = new JLabel();
			labels[i] = l;

			add(l);
		}

		setBorder(BorderFactory.createEmptyBorder(1, 4, 1, 4));

		reflectBaseDate();
	}

	public void setBaseDate(Date baseDate) {
		this.baseDate = baseDate;
		reflectBaseDate();
	}

	private void reflectBaseDate() {
		Calendar calendar = getCalendar(baseDate);
		calendar.set(Calendar.DAY_OF_MONTH, 1);

		int skipBefore = calendar.get(Calendar.DAY_OF_WEEK)
				- calendar.getFirstDayOfWeek();
		if (skipBefore < 0)
			skipBefore = 7 + skipBefore;

		int activeDays = calendar.getActualMaximum(Calendar.DAY_OF_MONTH);

		int numActiveWeeks = (activeDays + skipBefore) / 7;
		if ((activeDays + skipBefore) % 7 > 0)
			numActiveWeeks++;

		int startWeek = calendar.get(Calendar.WEEK_OF_YEAR);

		for (int i = 0; i < 6; i++) {
			labels[i].setText(startWeek > 0 && numActiveWeeks > 0 ? String
					.valueOf(startWeek) : "");

			labels[i].setForeground(isEnabled() ? UIManager
					.getColor("controlText") : UIManager
					.getColor("textInactiveText"));

			startWeek++;
			numActiveWeeks--;
		}

		setBackground(isEnabled() ? backgroundColorActive
				: backgroundColorInactive);
	}

	private Calendar getCalendar(Date date) {
		Calendar c = Calendar.getInstance(zone, locale);
		c.setTime(date);
		return c;
	}

	public void setLocale(Locale locale) {
		this.locale = locale;
		reflectBaseDate();
	}

	public void setZone(TimeZone zone) {
		this.zone = zone;
		reflectBaseDate();
	}

	public void paint(Graphics g) {
		FontMetrics fm = g.getFontMetrics(labels[0].getFont());
		Dimension dimension = new Dimension(fm.stringWidth("00") + 8, 1);
		setMinimumSize(dimension);
		setPreferredSize(dimension);
		super.paint(g);
	}

	public void setEnabled(boolean enabled) {
		super.setEnabled(enabled);
		reflectBaseDate();
	}

}
