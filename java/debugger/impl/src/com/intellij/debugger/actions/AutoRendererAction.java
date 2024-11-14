// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.events.DebuggerContextCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AutoRendererAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());

    final DebuggerManagerThreadImpl managerThread = debuggerContext.getManagerThread();
    if (managerThread != null) {
      final List<JavaValue> selectedValues = ViewAsGroup.getSelectedValues(e);
      if (!selectedValues.isEmpty()) {
        managerThread.schedule(new DebuggerContextCommandImpl(debuggerContext) {
          @Override
          public void threadAction(@NotNull SuspendContextImpl suspendContext) {
            for (JavaValue selectedValue : selectedValues) {
              selectedValue.getDescriptor().setRenderer(null);
            }
            DebuggerAction.refreshViews(e);
          }
        });
      }
    }
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
