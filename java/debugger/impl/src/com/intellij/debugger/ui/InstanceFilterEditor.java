// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.ui;

import com.intellij.debugger.JavaDebuggerBundle;
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

  @Override
  protected void addClassFilter() {
    String idString = Messages.showInputDialog(myProject,
                                               JavaDebuggerBundle.message("add.instance.filter.dialog.prompt"),
                                               JavaDebuggerBundle.message("add.instance.filter.dialog.title"),
                                               Messages.getQuestionIcon(),
                                               null,
                                               new InputValidatorEx() {
                                                 @Override
                                                 public @Nullable String getErrorText(String inputString) {
                                                   try {
                                                     Long.parseLong(inputString);
                                                     return null;
                                                   }
                                                   catch (NumberFormatException e) {
                                                     return JavaDebuggerBundle
                                                       .message("add.instance.filter.dialog.error.numeric.value.expected");
                                                   }
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

      IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(myTable, true));
    }
  }
}
