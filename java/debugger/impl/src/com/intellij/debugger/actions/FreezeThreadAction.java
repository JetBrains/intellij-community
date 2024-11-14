// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class FreezeThreadAction extends DebuggerAction {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    DebuggerTreeNodeImpl[] selectedNode = getSelectedNodes(e.getDataContext());
    if (selectedNode == null) {
      return;
    }
    final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();

    for (final DebuggerTreeNodeImpl debuggerTreeNode : selectedNode) {
      ThreadDescriptorImpl threadDescriptor = ((ThreadDescriptorImpl)debuggerTreeNode.getDescriptor());
      final ThreadReferenceProxyImpl thread = threadDescriptor.getThreadReference();

      if (!threadDescriptor.isFrozen()) {
        DebuggerManagerThreadImpl debuggerManagerThread = Objects.requireNonNull(debuggerContext.getManagerThread());
        debuggerManagerThread.schedule(new DebuggerCommandImpl() {
          @Override
          protected void action() {
            debuggerManagerThread.invoke(debugProcess.createFreezeThreadCommand(thread));
            ApplicationManager.getApplication().invokeLater(() -> debuggerTreeNode.calcValue());
          }
        });
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DebuggerTreeNodeImpl[] selectedNode = getSelectedNodes(e.getDataContext());
    if (selectedNode == null) {
      return;
    }
    DebugProcessImpl debugProcess = getDebuggerContext(e.getDataContext()).getDebugProcess();

    boolean visible = false;
    if (debugProcess != null) {
      visible = true;
      for (DebuggerTreeNodeImpl aSelectedNode : selectedNode) {
        NodeDescriptorImpl threadDescriptor = aSelectedNode.getDescriptor();
        if (!(threadDescriptor instanceof ThreadDescriptorImpl) || ((ThreadDescriptorImpl)threadDescriptor).isSuspended()) {
          visible = false;
          break;
        }
      }
    }

    e.getPresentation().setVisible(visible);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
