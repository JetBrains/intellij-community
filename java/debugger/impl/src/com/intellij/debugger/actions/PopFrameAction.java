// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author Eugene Zhuravlev
 */
package com.intellij.debugger.actions;

import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.debugger.ui.impl.watch.DebuggerTreeNodeImpl;
import com.intellij.debugger.ui.impl.watch.NodeDescriptorImpl;
import com.intellij.debugger.ui.impl.watch.StackFrameDescriptorImpl;
import com.intellij.openapi.actionSystem.ActionPlaces;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XDropFrameHandler;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class PopFrameAction extends DebuggerAction implements DumbAware {
  @NonNls public static final String ACTION_NAME = "Debugger.PopFrame";

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    final JavaStackFrame stackFrame = getStackFrame(e);
    if (stackFrame == null || stackFrame.getStackFrameProxy().isBottom()) {
      return;
    }
    final XDropFrameHandler handler = getDropFrameHandler(e);
    final JavaStackFrame frame = getSelectedStackFrame(e);
    if (frame != null && handler != null) {
      handler.drop(frame);
    }
  }

  static JavaStackFrame getStackFrame(AnActionEvent e) {
    StackFrameDescriptorImpl descriptor = getSelectedStackFrameDescriptor(e);
    if (descriptor != null) {
      return new JavaStackFrame(descriptor, false);
    }
    return getSelectedStackFrame(e);
  }

  static StackFrameProxyImpl getStackFrameProxy(AnActionEvent e) {
    DebuggerTreeNodeImpl node = getSelectedNode(e.getDataContext());
    if (node != null) {
      NodeDescriptorImpl descriptor = node.getDescriptor();
      if (descriptor instanceof StackFrameDescriptorImpl) {
        return ((StackFrameDescriptorImpl)descriptor).getFrameProxy();
      }
    }
    else {
      JavaStackFrame stackFrame = getSelectedStackFrame(e);
      if (stackFrame != null) {
        return stackFrame.getStackFrameProxy();
      }
    }
    return null;
  }

  @Nullable
  private static StackFrameDescriptorImpl getSelectedStackFrameDescriptor(AnActionEvent e) {
    DebuggerTreeNodeImpl selectedNode = getSelectedNode(e.getDataContext());
    if(selectedNode != null) {
      NodeDescriptorImpl descriptor = selectedNode.getDescriptor();
      if(descriptor instanceof StackFrameDescriptorImpl) {
        return (StackFrameDescriptorImpl)descriptor;
      }
    }
    return null;
  }

  @Nullable
  private static JavaStackFrame getSelectedStackFrame(AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      XDebugSession session = DebuggerUIUtil.getSession(e);
      if (session != null) {
        XStackFrame frame = session.getCurrentStackFrame();
        if (frame instanceof JavaStackFrame) {
          return ((JavaStackFrame)frame);
        }
      }
    }
    return null;
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    boolean enable = false;

    var xStackFrame = getSelectedStackFrame(e);
    var xDropFrameHandler = getDropFrameHandler(e);
    if (xStackFrame != null && xDropFrameHandler != null) {
      enable = xDropFrameHandler.canDrop(xStackFrame);
    }

    if((ActionPlaces.isMainMenuOrActionSearch(e.getPlace()) || ActionPlaces.DEBUGGER_TOOLBAR.equals(e.getPlace()))
        && xDropFrameHandler != null) {
      e.getPresentation().setEnabled(enable);
    }
    else {
      e.getPresentation().setVisible(enable);
    }
  }

  @Nullable
  private static XDropFrameHandler getDropFrameHandler(@NotNull AnActionEvent e) {
    var xSession = DebuggerUIUtil.getSession(e);
    return Optional.ofNullable(xSession)
      .map(XDebugSession::getDebugProcess)
      .map(XDebugProcess::getDropFrameHandler)
      .orElse(null);
  }
}
