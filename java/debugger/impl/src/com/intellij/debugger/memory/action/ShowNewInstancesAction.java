// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.memory.ui.ClassesFilteredView;
import com.intellij.debugger.memory.ui.InstancesWindow;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.memory.ui.ClassesTable;
import com.intellij.xdebugger.memory.ui.TypeInfo;
import com.intellij.xdebugger.memory.utils.InstancesProvider;
import com.sun.jdi.ReferenceType;

final class ShowNewInstancesAction extends ShowInstancesAction {
  private static final String POPUP_ELEMENT_LABEL = "Show New Instances";

  @Override
  protected boolean isEnabled(AnActionEvent e) {
    final ReferenceType selectedClass = DebuggerActionUtil.getSelectedClass(e);
    final InstancesProvider provider = e.getData(ClassesFilteredView.NEW_INSTANCES_PROVIDER_KEY);
    final int count = getInstancesCount(e);
    return super.isEnabled(e) && selectedClass != null && provider != null && count > 0;
  }

  @Override
  protected String getLabel() {
    return POPUP_ELEMENT_LABEL;
  }

  @Override
  protected int getInstancesCount(AnActionEvent e) {
    ClassesTable.ReferenceCountProvider countProvider = e.getData(ClassesTable.REF_COUNT_PROVIDER_KEY);
    TypeInfo selectedClass = DebuggerActionUtil.getSelectedTypeInfo(e);
    if (countProvider == null || selectedClass == null) {
      return -1;
    }

    return countProvider.getNewInstancesCount(selectedClass);
  }

  @Override
  protected void perform(AnActionEvent e) {
    final ReferenceType selectedClass = DebuggerActionUtil.getSelectedClass(e);
    final InstancesProvider provider = e.getData(ClassesFilteredView.NEW_INSTANCES_PROVIDER_KEY);
    final XDebugSession session = DebuggerUIUtil.getSession(e);
    if (selectedClass != null && provider != null && session != null) {
      new InstancesWindow(session, provider, selectedClass).show();
    }
  }
}
