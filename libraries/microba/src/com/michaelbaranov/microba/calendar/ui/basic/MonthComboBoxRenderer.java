package com.michaelbaranov.microba.calendar.ui.basic;

import javax.swing.*;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

class MonthComboBoxRenderer extends DefaultListCellRenderer {

  // private Locale locale;

  private TimeZone zone;

  private SimpleDateFormat dateFormat;

  MonthComboBoxRenderer(Locale locale, TimeZone zone) {
    // this.locale = locale;
    this.zone = zone;
    dateFormat = new SimpleDateFormat("MMMM", locale);
    dateFormat.setTimeZone(zone);
  }

  @Override
  public Component getListCellRendererComponent(JList list, Object value,
                                                int index, boolean isSelected, boolean cellHasFocus) {
    super.getListCellRendererComponent(list, value, index, isSelected,
        cellHasFocus);

    Date date = (Date) value;
    setText(dateFormat.format(date));

    return this;
  }

  @Override
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
