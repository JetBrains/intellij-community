// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.DebuggerManagerThreadImpl;
import com.intellij.debugger.engine.SuspendContextImpl;
import com.intellij.debugger.engine.SuspendManagerUtil;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class ResumeThreadAction extends DebuggerAction {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    DebuggerTreeNodeImpl[] selectedNode = getSelectedNodes(e.getDataContext());
    final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
    final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();

    if (debugProcess == null) return;

    //noinspection ConstantConditions
    for (final DebuggerTreeNodeImpl debuggerTreeNode : selectedNode) {
      final ThreadDescriptorImpl threadDescriptor = ((ThreadDescriptorImpl)debuggerTreeNode.getDescriptor());

      if (threadDescriptor.isSuspended()) {
        final ThreadReferenceProxyImpl thread = threadDescriptor.getThreadReference();
        DebuggerManagerThreadImpl debuggerManagerThread = debuggerContext.getManagerThread();
        Objects.requireNonNull(debuggerManagerThread).schedule(new DebuggerCommandImpl() {
          @Override
          protected void action() {
            SuspendContextImpl suspendingContext = SuspendManagerUtil.getSuspendingContext(debugProcess.getSuspendManager(), thread);
            if (suspendingContext != null) {
              debuggerManagerThread.invoke(debugProcess.createResumeThreadCommand(suspendingContext, thread));
            }
            ApplicationManager.getApplication().invokeLater(() -> debuggerTreeNode.calcValue());
          }
        });
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    DebuggerTreeNodeImpl[] selectedNodes = getSelectedNodes(e.getDataContext());

    boolean visible = false;
    boolean enabled = false;
    String text = JavaDebuggerBundle.message("action.resume.thread.text.resume");

    if (selectedNodes != null && selectedNodes.length > 0) {
      visible = true;
      enabled = true;
      for (DebuggerTreeNodeImpl selectedNode : selectedNodes) {
        final NodeDescriptorImpl threadDescriptor = selectedNode.getDescriptor();
        if (!(threadDescriptor instanceof ThreadDescriptorImpl) || !((ThreadDescriptorImpl)threadDescriptor).isSuspended()) {
          visible = false;
          break;
        }
      }
    }
    final Presentation presentation = e.getPresentation();
    presentation.setText(text);
    presentation.setVisible(visible);
    presentation.setEnabled(enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
