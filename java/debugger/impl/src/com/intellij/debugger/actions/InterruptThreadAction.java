// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.engine.events.DebuggerCommandImpl;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.ThreadReferenceProxyImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.ThreadDescriptorImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.MessageType;
import com.intellij.xdebugger.impl.XDebuggerManagerImpl;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

final class InterruptThreadAction extends DebuggerAction {
  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    final DebuggerTreeNodeImpl[] nodes = getSelectedNodes(e.getDataContext());
    if (nodes == null) {
      return;
    }

    final List<ThreadReferenceProxyImpl> threadsToInterrupt = new ArrayList<>();
    for (final DebuggerTreeNodeImpl debuggerTreeNode : nodes) {
      final NodeDescriptorImpl descriptor = debuggerTreeNode.getDescriptor();
      if (descriptor instanceof ThreadDescriptorImpl) {
        threadsToInterrupt.add(((ThreadDescriptorImpl)descriptor).getThreadReference());
      }
    }

    if (!threadsToInterrupt.isEmpty()) {
      final DebuggerContextImpl debuggerContext = getDebuggerContext(e.getDataContext());
      final DebugProcessImpl debugProcess = debuggerContext.getDebugProcess();
      if (debugProcess != null) {
        Objects.requireNonNull(debuggerContext.getManagerThread()).schedule(new DebuggerCommandImpl() {
          @Override
          protected void action() {
            boolean unsupported = false;
            for (ThreadReferenceProxyImpl thread : threadsToInterrupt) {
              try {
                thread.getThreadReference().interrupt();
              }
              catch (UnsupportedOperationException ignored) {
                unsupported = true;
              }
            }
            if (unsupported) {
              final Project project = debugProcess.getProject();
              XDebuggerManagerImpl.getNotificationGroup()
                .createNotification(JavaDebuggerBundle.message("thread.operation.interrupt.is.not.supported.by.vm"), MessageType.INFO).notify(project);
            }
          }
        });
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    final DebuggerTreeNodeImpl[] selectedNodes = getSelectedNodes(e.getDataContext());

    boolean visible = false;
    boolean enabled = false;

    if (selectedNodes != null && selectedNodes.length > 0) {
      visible = true;
      enabled = true;
      for (DebuggerTreeNodeImpl selectedNode : selectedNodes) {
        final NodeDescriptorImpl threadDescriptor = selectedNode.getDescriptor();
        if (!(threadDescriptor instanceof ThreadDescriptorImpl)) {
          visible = false;
          break;
        }
      }

      if (visible) {
        for (DebuggerTreeNodeImpl selectedNode : selectedNodes) {
          final ThreadDescriptorImpl threadDescriptor = (ThreadDescriptorImpl)selectedNode.getDescriptor();
          if (threadDescriptor.isFrozen() || threadDescriptor.isSuspended()) {
            enabled = false;
            break;
          }
        }
      }
    }
    final Presentation presentation = e.getPresentation();
    presentation.setText(JavaDebuggerBundle.messagePointer("action.interrupt.thread.text"));
    presentation.setEnabledAndVisible(visible && enabled);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.EDT;
  }
}
