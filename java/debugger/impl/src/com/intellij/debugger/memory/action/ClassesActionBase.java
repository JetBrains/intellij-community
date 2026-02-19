// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.action;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.memory.ui.TypeInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ClassesActionBase extends AnAction {
  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(isEnabled(e));
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    perform(e);
  }

  protected boolean isEnabled(AnActionEvent e) {
    final Project project = e.getProject();
    return project != null && !project.isDisposed();
  }

  protected abstract void perform(AnActionEvent e);

  protected @Nullable TypeInfo getSelectedClass(AnActionEvent e) {
    return DebuggerActionUtil.getSelectedTypeInfo(e);
  }
}
