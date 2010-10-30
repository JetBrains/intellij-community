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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.CommonBundle;

import javax.swing.*;
import java.awt.*;
import java.util.Date;


public class SelectDateDialog extends DialogWrapper {
  private CalendarView myCalendarView;
  private JPanel myPanel;

  public SelectDateDialog(Project project) {
    super(project, true);
    init();
    setTitle(CommonBundle.message("dialog.title.choose.date"));
    pack();
  }

  public SelectDateDialog(Component component) {
    super(component, true);
    init();
    setTitle(CommonBundle.message("dialog.title.choose.date"));
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
