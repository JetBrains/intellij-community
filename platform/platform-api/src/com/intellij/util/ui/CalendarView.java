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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.JBIntSpinner;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.*;
import java.text.DateFormatSymbols;
import java.text.ParseException;
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
  private static final String INCREASE_NUMBER_ID = "IncreaseNumber";
  private static final String DECREASE_NUMBER_ID = "DecreaseNumber";

  private final JComboBox<String> myDays = new ComboBox<>();
  private final JComboBox<String> myMonths = new ComboBox<>();
  private final JSpinner myYears = new JBIntSpinner(2022, 0, Integer.MAX_VALUE);

  private final JSpinner myHours = new JBIntSpinner(23, 0, 23);
  private final JSpinner myMinutes = new JBIntSpinner(59, 0, 59);
  private final JSpinner mySeconds = new JBIntSpinner(59, 0, 59);
  private final Calendar myCalendar = Calendar.getInstance();

  public CalendarView() {
    super(new GridLayout(2, 0));

    fillMonths();

    JSpinner.NumberEditor editor = new JSpinner.NumberEditor(myYears, "####");
    editor.getTextField().setColumns(4);
    myYears.setEditor(editor);
    myYears.addChangeListener(new ChangeListener() {
      @Override
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

    setFocusTraversalPolicy(new LayoutFocusTraversalPolicy());
    selectAllOnFocusGained(myYears);
    selectAllOnFocusGained(myHours);
    selectAllOnFocusGained(myMinutes);
    selectAllOnFocusGained(mySeconds);
    registerUpAndDownKeys(myYears);
    registerUpAndDownKeys(myHours);
    registerUpAndDownKeys(myMinutes);
    registerUpAndDownKeys(mySeconds);
  }

  private static void registerUpAndDownKeys(@NotNull JSpinner spinner) {
    JSpinner.DefaultEditor editor = ObjectUtils.tryCast(spinner.getEditor(), JSpinner.DefaultEditor.class);
    if (editor == null) return;
    JFormattedTextField field = editor.getTextField();
    field.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_UP, 0), INCREASE_NUMBER_ID);
    field.getInputMap(WHEN_FOCUSED).put(KeyStroke.getKeyStroke(KeyEvent.VK_DOWN, 0), DECREASE_NUMBER_ID);

    field.getActionMap().put(INCREASE_NUMBER_ID, getIncAction(spinner, field, 1));
    field.getActionMap().put(DECREASE_NUMBER_ID, getIncAction(spinner, field, -1));
  }

  @NotNull
  private static AbstractAction getIncAction(@NotNull JSpinner spinner, @NotNull JFormattedTextField field, int inc) {
    return new AbstractAction() {
      @Override
      public void actionPerformed(ActionEvent e) {
        int newValue = getIntValue(spinner) + inc;
        SpinnerNumberModel model = (SpinnerNumberModel)spinner.getModel();
        if (newValue <= (Integer)model.getMaximum() && newValue >= (Integer)model.getMinimum()) {
          boolean hasSelection = field.getSelectionStart() != field.getSelectionEnd();
          model.setValue(newValue);
          if (hasSelection) field.selectAll();
        }
      }
    };
  }

  private static void selectAllOnFocusGained(@NotNull JSpinner spinner) {
    JSpinner.DefaultEditor editor = ObjectUtils.tryCast(spinner.getEditor(), JSpinner.DefaultEditor.class);
    if (editor == null) return;
    editor.getTextField().addFocusListener(new FocusListener() {
      @Override
      public void focusGained(FocusEvent e) {
        ApplicationManager.getApplication().invokeLater(() -> {
          editor.getTextField().selectAll();
        });
      }

      @Override
      public void focusLost(FocusEvent e) { }
    });
  }

  @NotNull
  public Calendar getCalendar() {
    return myCalendar;
  }

  private void fillMonths() {
    DateFormatSymbols dateFormatSymbols = new DateFormatSymbols(Locale.getDefault());

    for (int i = Calendar.JANUARY; i <= Calendar.DECEMBER; i++)
      myMonths.addItem(dateFormatSymbols.getMonths()[i]);

    myMonths.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        refresh();
      }
    });
  }

  public void setDate(Date date) {
    myCalendar.setTime(date);
    myYears.setValue(Integer.valueOf(myCalendar.get(Calendar.YEAR)));
    myMonths.setSelectedIndex(myCalendar.get(Calendar.MONTH));
    myDays.setSelectedIndex(myCalendar.get(Calendar.DAY_OF_MONTH) - 1);

    myHours.setValue(Integer.valueOf(myCalendar.get(Calendar.HOUR_OF_DAY)));
    myMinutes.setValue(Integer.valueOf(myCalendar.get(Calendar.MINUTE)));
    mySeconds.setValue(Integer.valueOf(myCalendar.get(Calendar.SECOND)));
  }

  public JComponent getDaysCombo() {
    return myDays;
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
    commitSpinners();

    //noinspection MagicConstant
    myCalendar.set(getIntValue(myYears), myMonths.getSelectedIndex(), myDays.getSelectedIndex() + 1,
        getIntValue(myHours), getIntValue(myMinutes), getIntValue(mySeconds));

    return myCalendar.getTime();
  }

  private static int getIntValue(JSpinner spinner) {
    return ((Number)spinner.getModel().getValue()).intValue();
  }

  public void registerEnterHandler(final Runnable runnable) {
    new AnAction() {
      @Override
      public void update(@NotNull AnActionEvent e) {
        e.getPresentation().setEnabled(!myMonths.isPopupVisible() && !myDays.isPopupVisible());
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        runnable.run();
      }
    }.registerCustomShortcutSet(KeyEvent.VK_ENTER, 0, this);
  }

  private void commitSpinners() {
    try {
      myYears.commitEdit();
      myHours.commitEdit();
      myMinutes.commitEdit();
      mySeconds.commitEdit();
    }
    catch (ParseException ignore) {
    }
  }
}

