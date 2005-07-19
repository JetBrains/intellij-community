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
import com.intellij.util.ui.SelectDateDialog;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.text.DateFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

public class StandardVersionFilterComponent {
  private JPanel myPanel;

  public Component getStandardPanel() {
    return myPanel;
  }

  public interface Filter {
    boolean accepts(RepositoryVersion change);
  }

  private TextFieldWithBrowseButton myDateBefore;
  private TextFieldWithBrowseButton myDateAfter;
  private JCheckBox myUseDateBeforeFilter;
  private JTextField myNumBefore;
  private JCheckBox myUseDateAfterFilter;
  private JCheckBox myUseNumBeforeFilter;
  private JCheckBox myUseNumAfterFilter;
  private JTextField myNumAfter;

  private final Project myProject;
  private final DateFormat myDateFormat;

  public StandardVersionFilterComponent(Project project, DateFormat dateformat) {
    myProject = project;
    myDateFormat = dateformat;
  }

  protected void init(){
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
          myDateBefore.setText(myDateFormat.format(selectDateDialog.getDate()));
        }
      }
    });

    myDateAfter.getButton().addActionListener(new ActionListener() {
      public void actionPerformed(ActionEvent e) {
        final SelectDateDialog selectDateDialog = new SelectDateDialog(myProject);
        selectDateDialog.show();
        if (selectDateDialog.isOK()) {
          myDateAfter.setText(myDateFormat.format(selectDateDialog.getDate()));
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

  protected static void updatePair(JCheckBox checkBox, JComponent textField, ActionEvent e)  {
    textField.setEnabled(checkBox.isSelected());
    if (e != null && e.getSource() instanceof JCheckBox && ((JCheckBox)e.getSource()).isSelected()) {
      final Object source = e.getSource();
      if (source == checkBox && checkBox.isSelected()) {
        textField.requestFocus();
      }
    }

  }

  protected void updateAllEnabled(final ActionEvent e) {
    updatePair(myUseDateBeforeFilter,myDateBefore, e);
    updatePair(myUseDateAfterFilter,myDateAfter, e);
    updatePair(myUseNumBeforeFilter,myNumBefore, e);
    updatePair(myUseNumAfterFilter,myNumAfter, e);
  }

  protected void initValues() {
    final ChangeBrowserSettings settings = ChangeBrowserSettings.getSettings(myProject);
    myUseDateBeforeFilter.setSelected(settings.USE_DATE_BEFORE_FILTER);
    myUseDateAfterFilter.setSelected(settings.USE_DATE_AFTER_FILTER);
    myUseNumBeforeFilter.setSelected(settings.USE_CHANGE_BEFORE_FILTER);
    myUseNumAfterFilter.setSelected(settings.USE_CHANGE_AFTER_FILTER);

    myDateBefore.setText(settings.DATE_BEFORE);
    myDateAfter.setText(settings.DATE_AFTER);
    myNumBefore.setText(settings.CHANGE_BEFORE);
    myNumAfter.setText(settings.CHANGE_AFTER);
  }

  public void saveValues() {
    final ChangeBrowserSettings settings = ChangeBrowserSettings.getSettings(myProject);
    settings.USE_DATE_BEFORE_FILTER = myUseDateBeforeFilter.isSelected();
    settings.USE_DATE_AFTER_FILTER = myUseDateAfterFilter.isSelected();
    settings.USE_CHANGE_BEFORE_FILTER = myUseNumBeforeFilter.isSelected();
    settings.USE_CHANGE_AFTER_FILTER = myUseNumAfterFilter.isSelected();

    settings.DATE_BEFORE = myDateBefore.getText();
    settings.DATE_AFTER = myDateAfter.getText();
    settings.CHANGE_BEFORE = myNumBefore.getText();
    settings.CHANGE_AFTER = myNumAfter.getText();
  }


  protected void installCheckBoxListener(final ActionListener filterListener) {
    myUseDateBeforeFilter.addActionListener(filterListener);
    myUseDateAfterFilter.addActionListener(filterListener);
    myUseNumBeforeFilter.addActionListener(filterListener);
    myUseNumAfterFilter.addActionListener(filterListener);
  }

  protected List<Filter> createFilters() {
    final ArrayList<Filter> result = new ArrayList<Filter>();
    addDateFilter(myUseDateBeforeFilter, myDateBefore, result, true);
    addDateFilter(myUseDateAfterFilter, myDateAfter, result, false);

    if (myUseNumBeforeFilter.isSelected()) {
      try {
        final long numBefore = Long.parseLong(myNumBefore.getText());
        result.add(new Filter() {
          public boolean accepts(RepositoryVersion change) {
            return change.getNumber() <= numBefore;
          }
        });
      }
      catch (NumberFormatException e) {
        //ignore
      }
    }

    if (myUseNumAfterFilter.isSelected()) {
      try {
        final long numBefore = Long.parseLong(myNumAfter.getText());
        result.add(new Filter() {
          public boolean accepts(RepositoryVersion change) {
            return change.getNumber() >= numBefore;
          }
        });
      }
      catch (NumberFormatException e) {
        //ignore
      }
    }

    return result;
  }

  private void addDateFilter(final JCheckBox filter,
                             final TextFieldWithBrowseButton text,
                             final ArrayList<Filter> result,
                             final boolean before) {
    if (filter.isSelected()) {

      final String dateBefore = text.getText();
      try {
        final Date date = myDateFormat.parse(dateBefore);
        result.add(new Filter() {
          public boolean accepts(RepositoryVersion change) {
            final Date changeDate = change.getDate();
            if (changeDate == null) return false;

            return before ? changeDate.before(date) : changeDate.after(date);
          }
        });
      }
      catch (ParseException e) {
        //ignore
      }
    }
  }

  private Filter createFilter() {
    final List<Filter> filters = createFilters();
    return new Filter() {
      public boolean accepts(RepositoryVersion change) {
        for (Iterator<Filter> iterator = filters.iterator(); iterator.hasNext();) {
          Filter filter = iterator.next();
          if (!filter.accepts(change)) return false;
        }
        return true;
      }
    };
  }

  public void filterChanges(final List<RepositoryVersion> changeListInfos) {
    Filter filter = createFilter();
    for (Iterator<RepositoryVersion> iterator = changeListInfos.iterator(); iterator.hasNext();) {
      RepositoryVersion changeListInfo = iterator.next();
      if (!filter.accepts(changeListInfo)) {
        iterator.remove();
      }
    }
  }


}

