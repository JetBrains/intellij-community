/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.classFilter.ClassFilterEditor;
import com.intellij.util.IconUtil;

import javax.swing.*;

/**
 * User: lex
 * Date: Aug 29, 2003
 * Time: 2:38:30 PM
 */
public class InstanceFilterEditor extends ClassFilterEditor {
  public InstanceFilterEditor(Project project) {
    super(project);
  }

  protected void addClassFilter() {
    String idString = Messages.showInputDialog(myProject, DebuggerBundle.message("add.instance.filter.dialog.prompt"), DebuggerBundle.message("add.instance.filter.dialog.title"), Messages.getQuestionIcon());
    if (idString != null) {
      ClassFilter filter = createFilter(idString);
      myTableModel.addRow(filter);
      int row = myTableModel.getRowCount() - 1;
      myTable.getSelectionModel().setSelectionInterval(row, row);
      myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

      myTable.requestFocus();
    }
  }

  protected String getAddButtonText() {
    return DebuggerBundle.message("button.add");
  }

  @Override
  protected Icon getAddButtonIcon() {
    return IconUtil.getAddIcon();
  }

  @Override
  protected boolean addPatternButtonVisible() {
    return false;
  }

  protected ClassFilter createFilter(String pattern) {
    try {
      Long.parseLong(pattern);
      return super.createFilter(pattern);
    } catch (NumberFormatException e) {
      Messages.showMessageDialog(this, DebuggerBundle.message("add.instance.filter.dialog.error.numeric.value.expected"), DebuggerBundle.message("add.instance.filter.dialog.title"), Messages.getErrorIcon());
      return null;
    }
  }
}
