// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.ui;

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.UIBundle;

import javax.swing.*;
import java.awt.*;
import java.util.Date;

/**
 * @deprecated unused
 */
@Deprecated(forRemoval = true)
@SuppressWarnings("DeprecatedIsStillUsed")
public class SelectDateDialog extends DialogWrapper {
  private CalendarView myCalendarView;
  private JPanel myPanel;

  public SelectDateDialog(Component component) {
    super(component, true);
    init();
    setTitle(UIBundle.message("date.dialog.title"));
    pack();
  }

  @Override
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