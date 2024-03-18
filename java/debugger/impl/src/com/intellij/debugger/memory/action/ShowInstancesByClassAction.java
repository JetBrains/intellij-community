// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.action;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.memory.filtering.ClassInstancesProvider;
import com.intellij.debugger.memory.ui.InstancesWindow;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;

public class ShowInstancesByClassAction extends DebuggerTreeAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  protected boolean isEnabled(@NotNull XValueNodeImpl node, @NotNull AnActionEvent e) {
    final ObjectReference ref = getObjectReference(node);
    final boolean enabled = ref != null && ref.virtualMachine().canGetInstanceInfo();
    if (enabled) {
      final String text = JavaDebuggerBundle.message("action.show.objects.text", StringUtil.getShortName(ref.referenceType().name()));
      e.getPresentation().setText(text);
    }

    return enabled;
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    final Project project = e.getProject();
    if (project != null) {
      final XDebugSession debugSession = DebuggerUIUtil.getSession(e);
      final ObjectReference ref = getObjectReference(node);
      if (debugSession != null && ref != null) {
        final ReferenceType referenceType = ref.referenceType();
        new InstancesWindow(debugSession, new ClassInstancesProvider(referenceType), referenceType).show();
      }
    }
  }
}
