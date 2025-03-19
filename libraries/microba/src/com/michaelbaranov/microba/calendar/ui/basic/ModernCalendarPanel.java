package com.michaelbaranov.microba.calendar.ui.basic;

import javax.swing.*;
import java.awt.*;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.*;

class ModernCalendarPanel extends JPanel implements PropertyChangeListener {

  public static final String PROPERTY_NAME_DATE = "date";

  public static final String PROPERTY_NAME_LOCALE = "locale";

  public static final String PROPERTY_NAME_ZONE = "zone";

  private Date date;

  private Locale locale;

  private TimeZone zone;

  private final YearSpinnerModel yearSpinnerModel;

  private final NoGroupingSpinner yearSpinner;

  private final MonthComboBoxModel monthComboBoxModel;

  private final MonthComboBoxRenderer monthComboBoxRenderer;

  private final JComboBox monthCombo;

  private final Set<JComponent> focusableComponents = new HashSet<>();

  public ModernCalendarPanel(Date aDate, Locale aLocale, TimeZone zone) {
    this.date = aDate;
    this.locale = aLocale;
    this.zone = zone;

    monthComboBoxModel = new MonthComboBoxModel(aDate, aLocale, zone);
    monthComboBoxRenderer = new MonthComboBoxRenderer(aLocale, zone);
    monthCombo = new JComboBox(monthComboBoxModel);

    monthCombo.setRenderer(monthComboBoxRenderer);

    yearSpinnerModel = new YearSpinnerModel(aDate, aLocale, zone);
    yearSpinner = new NoGroupingSpinner(yearSpinnerModel);

    setLayout(new GridBagLayout());
    add(monthCombo, new GridBagConstraints(0, 0, 1, 1, 1.0, 0,
        GridBagConstraints.EAST, GridBagConstraints.NONE, new Insets(0,
            0, 3, 0), 0, 0));
    add(yearSpinner, new GridBagConstraints(1, 0, 1, 1, 0, 0,
        GridBagConstraints.EAST, GridBagConstraints.VERTICAL,
        new Insets(0, 3, 3, 0), 0, 0));

    focusableComponents.add(yearSpinner);
    focusableComponents.add(monthCombo);

    monthComboBoxModel
        .addPropertyChangeListener(new PropertyChangeListener() {

          @Override
          public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(
                MonthComboBoxModel.PROPERTY_NAME_DATE)) {
              Date newDate = (Date) evt.getNewValue();
              setDate(newDate);
            }
          }
        });
    yearSpinnerModel
        .addPropertyChangeListener(new PropertyChangeListener() {

          @Override
          public void propertyChange(PropertyChangeEvent evt) {
            if (evt.getPropertyName().equals(
                YearSpinnerModel.PROPERTY_NAME_DATE)) {
              Date newDate = (Date) evt.getNewValue();
              setDate(newDate);
            }
          }
        });
    this.addPropertyChangeListener(this);

  }

  @Override
  public void setEnabled(boolean enabled) {
    super.setEnabled(enabled);
    monthCombo.setEnabled(enabled);
    yearSpinner.setEnabled(enabled);
  }

  @Override
  public void setFocusable(boolean focusable) {
    super.setFocusable(focusable);
    monthCombo.setFocusable(focusable);
    yearSpinner.setFocusable(focusable);
  }

  public Date getDate() {
    return date;
  }

  public void setDate(Date date) {
    Date oldDate = this.date;
    this.date = date;
    firePropertyChange(PROPERTY_NAME_DATE, oldDate, date);
    monthComboBoxModel.setDate(date);
    yearSpinnerModel.setDate(date);
  }

  @Override
  public Locale getLocale() {
    return locale;
  }

  @Override
  public void setLocale(Locale locale) {
    Locale old = this.locale;
    this.locale = locale;
    monthComboBoxRenderer.setLocale(locale);
    monthComboBoxModel.setLocale(locale);
    yearSpinnerModel.setLocale(locale);
    firePropertyChange(PROPERTY_NAME_LOCALE, old, locale);
  }

  public Collection<JComponent> getFocusableComponents() {
    return focusableComponents;
  }

  public TimeZone getZone() {
    return zone;
  }

  public void setZone(TimeZone zone) {
    TimeZone old = this.zone;
    this.zone = zone;
    monthComboBoxRenderer.setZone(zone);
    monthComboBoxModel.setZone(zone);
    yearSpinnerModel.setZone(zone);
    firePropertyChange(PROPERTY_NAME_ZONE, old, zone);
  }

  @Override
  public void propertyChange(PropertyChangeEvent evt) {
    if (evt.getPropertyName().equals("focusable")) {
      boolean value = ((Boolean) evt.getNewValue()).booleanValue();
      yearSpinner.setFocusable(value);
      Component[] children = yearSpinner.getEditor().getComponents();
      for (Component child : children) child.setFocusable(value);
      monthCombo.setFocusable(value);
    }
    if (evt.getPropertyName().equals("enabled")) {
      boolean value = ((Boolean) evt.getNewValue()).booleanValue();
      yearSpinner.setEnabled(value);
      monthCombo.setEnabled(value);
    }
  }

}
