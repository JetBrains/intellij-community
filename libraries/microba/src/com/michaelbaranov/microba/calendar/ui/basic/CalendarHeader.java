package com.michaelbaranov.microba.calendar.ui.basic;

import com.michaelbaranov.microba.Microba;
import com.michaelbaranov.microba.calendar.CalendarColors;
import com.michaelbaranov.microba.calendar.CalendarPane;
import com.michaelbaranov.microba.calendar.HolidayPolicy;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

class CalendarHeader extends JPanel {

  private Locale locale;

  private TimeZone zone;

  private Date date;

  private HolidayPolicy holidayPolicy;

  private final Color backgroundColorActive;

  private final Color backgroundColorInactive;

  private final Color foregroundColorActive;

  private final Color foregroundColorInactive;

  private final Color foregroundColorWeekendEnabled;

  private final Color foregroundColorWeekendDisabled;

  CalendarHeader(CalendarPane peer, Date date, Locale locale,
                 TimeZone zone, HolidayPolicy holidayPolicy) {
    super();

    backgroundColorActive = Microba.getOverridenColor(
      CalendarColors.COLOR_CALENDAR_HEADER_BACKGROUND_ENABLED, peer,
      UIManager.getColor("activeCaption"));
    backgroundColorInactive = Microba.getOverridenColor(
      CalendarColors.COLOR_CALENDAR_HEADER_BACKGROUND_DISABLED, peer,
      UIManager.getColor("inactiveCaption"));
    foregroundColorActive = Microba.getOverridenColor(
      CalendarColors.COLOR_CALENDAR_HEADER_FOREGROUND_ENABLED, peer,
      UIManager.getColor("controlText"));
    foregroundColorInactive = Microba.getOverridenColor(
      CalendarColors.COLOR_CALENDAR_HEADER_FOREGROUND_DISABLED, peer,
      UIManager.getColor("textInactiveText"));
    foregroundColorWeekendEnabled = Microba.getOverridenColor(
      CalendarColors.COLOR_CALENDAR_HEADER_FOREGROUND_WEEKEND_ENABLED,
        peer, Color.RED);
    foregroundColorWeekendDisabled = Microba.getOverridenColor(
      CalendarColors.COLOR_CALENDAR_HEADER_FOREGROUND_WEEKEND_DISABLED,
        peer, foregroundColorInactive);

    this.locale = locale;
    this.zone = zone;
    this.date = date;
    this.holidayPolicy = holidayPolicy;
    reflectData();
  }

  private void reflectData() {

    Calendar cal = Calendar.getInstance(zone, locale);
    cal.setTime(date == null ? new Date() : date);

    SimpleDateFormat fmt = new SimpleDateFormat("E", locale);
    fmt.setTimeZone(zone);

    int numDaysInWeek = cal.getActualMaximum(Calendar.DAY_OF_WEEK)
        - cal.getActualMinimum(Calendar.DAY_OF_WEEK) + 1;
    int firstDayOfWeek = cal.getFirstDayOfWeek();

    cal.set(Calendar.DAY_OF_WEEK, firstDayOfWeek);

    removeAll();
    setLayout(new GridLayout(1, numDaysInWeek, 2, 2));

    setBackground(isEnabled() ? backgroundColorActive
        : backgroundColorInactive);

    for (int i = 0; i < numDaysInWeek; i++) {
      JLabel label = new JLabel();
      // TODO: add option to control limit length:
      label.setText(fmt.format(cal.getTime())/* .substring(0,1) */);
      label.setForeground(isEnabled() ? foregroundColorActive
          : foregroundColorInactive);
      label.setHorizontalAlignment(SwingConstants.CENTER);
      label.setBorder(BorderFactory.createEmptyBorder(3, 0, 3, 0));
      Font boldFont = label.getFont().deriveFont(Font.BOLD);
      label.setFont(boldFont);
      add(label);

      boolean isHoliday = false;
      if (holidayPolicy != null) {
        isHoliday = holidayPolicy.isWeekend(this, cal);
      }

      if (isHoliday)
        label.setForeground(isEnabled() ? foregroundColorWeekendEnabled
            : foregroundColorWeekendDisabled);

      cal.add(Calendar.DAY_OF_WEEK, 1);
    }
    setBorder(BorderFactory.createEmptyBorder(0, 1, 0, 1));
    revalidate();
    repaint();

  }

  @Override
  public void setLocale(Locale locale) {
    this.locale = locale;
    reflectData();
  }

  public void setDate(Date date) {
    this.date = date;
    reflectData();
  }

  public TimeZone getZone() {
    return zone;
  }

  public void setZone(TimeZone zone) {
    this.zone = zone;
    reflectData();
  }

  public void setHolidayPolicy(HolidayPolicy holidayPolicy) {
    this.holidayPolicy = holidayPolicy;
    reflectData();

  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    reflectData();
  }

}
