package com.intellij.util.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;

import javax.swing.*;
import java.util.Date;


public class SelectDateDialog extends DialogWrapper {
  private CalendarView myCalendarView;
  private JPanel myPanel;

  public SelectDateDialog(Project project) {
    super(project, true);
    init();
    setTitle("Choose Date");
    pack();
  }

  protected JComponent createCenterPanel() {
    return myPanel;
  }

  public Date getDate() {
    return myCalendarView.getDate();
  }

  public void setDate(Date date) {
    myCalendarView.setDate(date);
  }
}
