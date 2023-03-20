// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.JavaValue;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.memory.ui.StackFramePopup;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.debugger.ui.breakpoints.StackCapturingLineBreakpoint;
import com.intellij.debugger.ui.impl.watch.ValueDescriptorImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ShowRelatedStackAction extends AnAction {
  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    List<StackFrameItem> stack = getRelatedStack(e);
    if (project != null && stack != null) {
      DebugProcessImpl debugProcess = DebuggerAction.getDebuggerContext(e.getDataContext()).getDebugProcess();
      if (debugProcess == null) {
        return;
      }

      StackFramePopup.show(stack, debugProcess);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    List<StackFrameItem> stack = getRelatedStack(e);
    e.getPresentation().setEnabledAndVisible(stack != null);
  }

  @Nullable
  private static List<StackFrameItem> getRelatedStack(AnActionEvent e) {
    List<JavaValue> values = ViewAsGroup.getSelectedValues(e);
    if (values.size() == 1) {
      ValueDescriptorImpl descriptor = values.get(0).getDescriptor();
      if (descriptor.isValueReady()) {
        Value value = descriptor.getValue();
        if (value instanceof ObjectReference) {
          DebuggerContextImpl debuggerContext = DebuggerAction.getDebuggerContext(e.getDataContext());
          return StackCapturingLineBreakpoint.getRelatedStack((ObjectReference)value, debuggerContext.getDebugProcess());
        }
      }
    }

    return null;
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
