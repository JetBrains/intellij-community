// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.ui;

import com.intellij.debugger.DebuggerBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.InputValidatorEx;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.ui.classFilter.ClassFilter;
import org.jetbrains.annotations.Nullable;

public class InstanceFilterEditor extends PatternFilterEditor {
  public InstanceFilterEditor(Project project) {
    super(project);
  }

  protected void addClassFilter() {
    String idString = Messages.showInputDialog(myProject,
                                               DebuggerBundle.message("add.instance.filter.dialog.prompt"),
                                               DebuggerBundle.message("add.instance.filter.dialog.title"),
                                               Messages.getQuestionIcon(),
                                               null,
                                               new InputValidatorEx() {
                                                 @Nullable
                                                 @Override
                                                 public String getErrorText(String inputString) {
                                                   try {
                                                     //noinspection ResultOfMethodCallIgnored
                                                     Long.parseLong(inputString);
                                                     return null;
                                                   } catch (NumberFormatException e) {
                                                     return DebuggerBundle.message("add.instance.filter.dialog.error.numeric.value.expected");
                                                   }
                                                 }

                                                 @Override
                                                 public boolean checkInput(String inputString) {
                                                   return getErrorText(inputString) == null;
                                                 }

                                                 @Override
                                                 public boolean canClose(String inputString) {
                                                   return getErrorText(inputString) == null;
                                                 }
                                               });
    if (idString != null) {
      ClassFilter filter = createFilter(idString);
      myTableModel.addRow(filter);
      int row = myTableModel.getRowCount() - 1;
      myTable.getSelectionModel().setSelectionInterval(row, row);
      myTable.scrollRectToVisible(myTable.getCellRect(row, 0, true));

      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> {
        IdeFocusManager.getGlobalInstance().requestFocus(myTable, true);
      });
    }
  }
}
