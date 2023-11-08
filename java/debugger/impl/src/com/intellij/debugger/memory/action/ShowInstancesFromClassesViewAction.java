// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.memory.ui.InstancesWindow;
import com.intellij.debugger.memory.ui.JavaTypeInfo;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.memory.ui.ClassesTable;
import com.intellij.xdebugger.memory.ui.TypeInfo;

public class ShowInstancesFromClassesViewAction extends ShowInstancesAction {
  private static final String POPUP_ELEMENT_LABEL = "Show Instances";

  @Override
  protected void perform(AnActionEvent e) {
    final Project project = e.getProject();
    final TypeInfo selectedClass = getSelectedClass(e);
    if (project != null && selectedClass != null) {
      final XDebugSession debugSession = DebuggerUIUtil.getSession(e);
      if (debugSession != null) {
        new InstancesWindow(debugSession, selectedClass::getInstances, ((JavaTypeInfo)selectedClass).getReferenceType()).show();
      }
    }
  }

  @Override
  protected String getLabel() {
    return POPUP_ELEMENT_LABEL;
  }

  @Override
  protected int getInstancesCount(AnActionEvent e) {
    ClassesTable.ReferenceCountProvider countProvider = e.getData(ClassesTable.REF_COUNT_PROVIDER_KEY);
    TypeInfo selectedClass = getSelectedClass(e);
    if (countProvider == null || selectedClass == null) {
      return -1;
    }

    return countProvider.getTotalCount(selectedClass);
  }
}
