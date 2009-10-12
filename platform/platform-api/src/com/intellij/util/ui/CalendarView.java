/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.util.ui;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DateFormatSymbols;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * author: lesya
 */
public class CalendarView extends JPanel {

  private final static int[] DAYS_IN_THE_MONTH = new int[]{
    31,
    -1,
    31,
    30,
    31,
    30,
    31,
    31,
    30,
    31,
    30,
    31
  };

  private final JComboBox myDays = new JComboBox();
  private final JComboBox myMonths = new JComboBox();
  private final JSpinner myYears = new JSpinner(new IntegerSpinnerModel(0, -1));

  private final JSpinner myHours = new JSpinner(new IntegerSpinnerModel(0, 24));
  private final JSpinner myMinutes = new JSpinner(new IntegerSpinnerModel(0, 60));
  private final JSpinner mySeconds = new JSpinner(new IntegerSpinnerModel(0, 60));
  private final Calendar myCalendar = Calendar.getInstance();

  public CalendarView() {
    super(new GridLayout(2, 0));

    fillMonths();


    myYears.addChangeListener(new ChangeListener() {
      public void stateChanged(ChangeEvent e) {
        refresh();
      }
    });

    setDate(new Date());

    addDateFields();
    addTimeFields();

    int height = Math.max(myYears.getPreferredSize().height, myDays.getPreferredSize().height);
    height = Math.max(myMonths.getPreferredSize().height, height);

    myDays.setPreferredSize(new Dimension(myDays.getPreferredSize().width, height));
    myYears.setPreferredSize(new Dimension(myYears.getPreferredSize().width, height));
    myMonths.setPreferredSize(new Dimension(myMonths.getPreferredSize().width, height));

    Dimension preferredSize = getPreferredSize();
    setMaximumSize(preferredSize);
    setMaximumSize(preferredSize);
  }

  private void fillMonths() {
    DateFormatSymbols dateFormatSymbols = new DateFormatSymbols(Locale.getDefault());

    for (int i = Calendar.JANUARY; i <= Calendar.DECEMBER; i++)
      myMonths.addItem(dateFormatSymbols.getMonths()[i]);

    myMonths.addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        refresh();
      }
    });
  }

  public void setDate(Date date) {
    myCalendar.setTime(date);
    myYears.setValue(new Integer(myCalendar.get(Calendar.YEAR)));
    myMonths.setSelectedIndex(myCalendar.get(Calendar.MONTH));
    myDays.setSelectedIndex(myCalendar.get(Calendar.DAY_OF_MONTH) - 1);

    myHours.setValue(new Integer(myCalendar.get(Calendar.HOUR_OF_DAY)));
    myMinutes.setValue(new Integer(myCalendar.get(Calendar.MINUTE)));
    mySeconds.setValue(new Integer(myCalendar.get(Calendar.SECOND)));
  }

  private void addTimeFields() {
    JPanel timePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    timePanel.add(myHours);
    timePanel.add(new JLabel(" : "));
    timePanel.add(myMinutes);
    timePanel.add(new JLabel(" : "));
    timePanel.add(mySeconds);
    add(timePanel);
  }

  private void addDateFields() {
    JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
    datePanel.add(myDays);
    datePanel.add(myMonths);
    datePanel.add(myYears);
    add(datePanel);
  }

  private void refresh() {
    int days = DAYS_IN_THE_MONTH[myMonths.getSelectedIndex()];
    if (days > 0)
      fillDays(days);
    else
      fillDays(daysInTheFebruary());
  }

  private void fillDays(int days) {
    int selectedDayIndex = myDays.getSelectedIndex();
    myDays.removeAllItems();
    for (int i = 0; i < days; i++)
      myDays.addItem(String.valueOf(i + 1));
    if (selectedDayIndex < myDays.getItemCount())
      myDays.setSelectedIndex(selectedDayIndex);
    else
      myDays.setSelectedIndex(myDays.getItemCount() - 1);
  }

  private int daysInTheFebruary() {
    int year = Integer.parseInt(myYears.getValue().toString());
    if (year % 4 > 0) return 29;
    if (year % 100 > 0) return 29;
    return 28;
  }

  public Date getDate() {
    JSpinner spinner = myYears;
    myCalendar.set(getIntValue(spinner), myMonths.getSelectedIndex(), myDays.getSelectedIndex() + 1,
        getIntValue(myHours), getIntValue(myMinutes), getIntValue(mySeconds));

    return myCalendar.getTime();
  }

  private int getIntValue(JSpinner spinner) {
    return ((IntegerSpinnerModel) spinner.getModel()).getIntValue();
  }
}

