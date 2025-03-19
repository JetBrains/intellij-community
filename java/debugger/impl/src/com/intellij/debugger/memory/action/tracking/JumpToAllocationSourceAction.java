// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.action.tracking;

import com.intellij.debugger.DebuggerManager;
import com.intellij.debugger.engine.DebugProcessImpl;
import com.intellij.debugger.memory.action.DebuggerTreeAction;
import com.intellij.debugger.memory.component.MemoryViewDebugProcessData;
import com.intellij.debugger.memory.ui.StackFramePopup;
import com.intellij.debugger.memory.utils.StackFrameItem;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl;
import com.sun.jdi.ObjectReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JumpToAllocationSourceAction extends DebuggerTreeAction {
  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setVisible(getStack(e) != null);
  }

  @Override
  protected void perform(XValueNodeImpl node, @NotNull String nodeName, AnActionEvent e) {
    final Project project = e.getProject();
    final List<StackFrameItem> stack = getStack(e);
    if (project != null && stack != null) {
      final XDebugSession session = DebuggerUIUtil.getSession(e);
      if (session != null) {
        DebugProcessImpl process = (DebugProcessImpl)DebuggerManager.getInstance(project)
          .getDebugProcess(session.getDebugProcess().getProcessHandler());
        StackFramePopup.show(stack, process);
      }
    }
  }

  private static @Nullable List<StackFrameItem> getStack(AnActionEvent e) {
    final Project project = e.getProject();
    final XValueNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    final ObjectReference ref = selectedNode != null ? getObjectReference(selectedNode) : null;
    if (project == null || ref == null) {
      return null;
    }

    final XDebugSession session = DebuggerUIUtil.getSession(e);
    if (session != null) {
      final MemoryViewDebugProcessData data =
        DebuggerManager.getInstance(project).getDebugProcess(session.getDebugProcess().getProcessHandler()).getUserData(
          MemoryViewDebugProcessData.KEY);
      return data != null ? data.getTrackedStacks().getStack(ref) : null;
    }

    return null;
  }
}
