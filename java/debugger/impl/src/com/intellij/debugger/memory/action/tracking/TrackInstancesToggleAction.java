// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.action.tracking;

import com.intellij.debugger.memory.action.DebuggerActionUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.memory.component.InstancesTracker;
import com.intellij.xdebugger.memory.tracking.TrackingType;
import com.sun.jdi.ArrayType;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

public class TrackInstancesToggleAction extends ToggleAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    ReferenceType selectedClass = DebuggerActionUtil.getSelectedClass(e);
    if (selectedClass instanceof ArrayType) {
      e.getPresentation().setEnabled(false);
    }
    else {
      super.update(e);
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    ReferenceType selectedClass = DebuggerActionUtil.getSelectedClass(e);
    final Project project = e.getProject();
    if (project != null && selectedClass != null && !project.isDisposed()) {
      InstancesTracker tracker = InstancesTracker.getInstance(project);
      return tracker.isTracked(selectedClass.name());
    }

    return false;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    final ReferenceType selectedClass = DebuggerActionUtil.getSelectedClass(e);
    final Project project = e.getProject();
    if (selectedClass != null && project != null && !project.isDisposed()) {
      InstancesTracker tracker = InstancesTracker.getInstance(project);
      boolean isAlreadyTracked = tracker.isTracked(selectedClass.name());

      if (isAlreadyTracked && !state) {
        tracker.remove(selectedClass.name());
      }

      if (!isAlreadyTracked && state) {
        tracker.add(selectedClass.name(), TrackingType.CREATION);
      }
    }
  }
}
