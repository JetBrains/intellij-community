/*
 * Copyright 2000-2005 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.versionBrowser;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.TextFieldWithBrowseButton;
import com.intellij.openapi.vcs.ui.RefreshableOnComponent;
import com.intellij.util.ui.SelectDateDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.Date;

public abstract class StandardVersionFilterComponent implements RefreshableOnComponent {
  private JPanel myPanel;

  public JPanel getVersionNumberPanel() {
    return myVersionNumberPanel;
  }

  public JPanel getDatePanel() {
    return myDatePanel;
  }

  public Component getStandardPanel() {
    return myPanel;
  }

  private TextFieldWithBrowseButton myDateBefore;
  private TextFieldWithBrowseButton myDateAfter;
  private JCheckBox myUseDateBeforeFilter;
  private JTextField myNumBefore;
  private JCheckBox myUseDateAfterFilter;
  private JCheckBox myUseNumBeforeFilter;
  private JCheckBox myUseNumAfterFilter;
  private JTextField myNumAfter;
  private JPanel myDatePanel;
  private JPanel myVersionNumberPanel;

  private final Project myProject;
  private final DateFormat myDateFormat;

  public StandardVersionFilterComponent(Project project, DateFormat dateformat) {
    myProject = project;
    myDateFormat = dateformat;
  }

  protected void init() {
    installBrowseDateActions();
    installCheckBoxesListeners();
    initValues();
    updateAllEnabled(null);
  }

  private void installBrowseDateActions() {
    myDateBefore.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final SelectDateDialog selectDateDialog = new SelectDateDialog(myProject);
        selectDateDialog.show();
        if (selectDateDialog.isOK()) {
          myDateBefore.setText(format(selectDateDialog.getDate()));
        }
      }
    });

    myDateAfter.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final SelectDateDialog selectDateDialog = new SelectDateDialog(myProject);
        selectDateDialog.show();
        if (selectDateDialog.isOK()) {
          myDateAfter.setText(format(selectDateDialog.getDate()));
        }
      }
    });
  }

  private void installCheckBoxesListeners() {
    final ActionListener filterListener = new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        updateAllEnabled(e);
      }
    };


    installCheckBoxListener(filterListener);
  }

  protected static void updatePair(JCheckBox checkBox, JComponent textField, ActionEvent e) {
    textField.setEnabled(checkBox.isSelected());
    if (e != null && e.getSource() instanceof JCheckBox && ((JCheckBox)e.getSource()).isSelected()) {
      final Object source = e.getSource();
      if (source == checkBox && checkBox.isSelected()) {
        textField.requestFocus();
      }
    }

  }

  protected void updateAllEnabled(final ActionEvent e) {
    updatePair(myUseDateBeforeFilter, myDateBefore, e);
    updatePair(myUseDateAfterFilter, myDateAfter, e);
    updatePair(myUseNumBeforeFilter, myNumBefore, e);
    updatePair(myUseNumAfterFilter, myNumAfter, e);
  }

  protected void initValues() {
    final ChangeBrowserSettings settings = ChangeBrowserSettings.getSettings(myProject);
    myUseDateBeforeFilter.setSelected(settings.USE_DATE_BEFORE_FILTER);
    myUseDateAfterFilter.setSelected(settings.USE_DATE_AFTER_FILTER);
    myUseNumBeforeFilter.setSelected(settings.USE_CHANGE_BEFORE_FILTER);
    myUseNumAfterFilter.setSelected(settings.USE_CHANGE_AFTER_FILTER);

    myDateBefore.setText(format(settings.getDateBefore()));
    myDateAfter.setText(format(settings.getDateAfter()));
    myNumBefore.setText(settings.CHANGE_BEFORE);
    myNumAfter.setText(settings.CHANGE_AFTER);
  }

  private String format(final Date date) {
    if (date == null) return "";
    return myDateFormat.format(date);
  }

  public void saveValues() {
    final ChangeBrowserSettings settings = ChangeBrowserSettings.getSettings(myProject);
    settings.USE_DATE_BEFORE_FILTER = myUseDateBeforeFilter.isSelected();
    settings.USE_DATE_AFTER_FILTER = myUseDateAfterFilter.isSelected();
    settings.USE_CHANGE_BEFORE_FILTER = myUseNumBeforeFilter.isSelected();
    settings.USE_CHANGE_AFTER_FILTER = myUseNumAfterFilter.isSelected();

    settings.setDateBefore(parse(myDateBefore.getText()));
    settings.setDateAfter(parse(myDateAfter.getText()));
    settings.CHANGE_BEFORE = myNumBefore.getText();
    settings.CHANGE_AFTER = myNumAfter.getText();
  }

  private Date parse(final String text) {
    try {
      return myDateFormat.parse(text);
    }
    catch (ParseException e) {
      return null;
    }
  }


  protected void installCheckBoxListener(final ActionListener filterListener) {
    myUseDateBeforeFilter.addActionListener(filterListener);
    myUseDateAfterFilter.addActionListener(filterListener);
    myUseNumBeforeFilter.addActionListener(filterListener);
    myUseNumAfterFilter.addActionListener(filterListener);
  }

  public void refresh() {
  }

  public void saveState() {
    saveValues();
  }

  public void restoreState() {
    initValues();
  }

}

