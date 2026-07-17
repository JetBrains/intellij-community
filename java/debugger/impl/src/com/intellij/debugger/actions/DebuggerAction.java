// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * Class DebuggerAction
 * @author Jeka
 */
package com.intellij.debugger.actions;


import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.engine.JavaDebugProcess;
import com.intellij.debugger.engine.JavaStackFrame;
import com.intellij.debugger.impl.DebuggerContextImpl;
import com.intellij.debugger.jdi.StackFrameProxyImpl;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.XDebugProcess;
import com.intellij.xdebugger.XDebugSession;
import com.intellij.xdebugger.frame.XStackFrame;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class DebuggerAction extends AnAction {
  public static @NotNull DebuggerContextImpl getDebuggerContext(DataContext dataContext) {
    Project project = CommonDataKeys.PROJECT.getData(dataContext);
    return project != null ? DebuggerManagerEx.getInstanceEx(project).getContext() : DebuggerContextImpl.EMPTY_CONTEXT;
  }

  public static void refreshViews(@Nullable XDebugSession session) {
    if (session != null) {
      XDebugProcess process = session.getDebugProcess();
      if (process instanceof JavaDebugProcess) {
        ((JavaDebugProcess)process).saveNodeHistory();
      }
      session.rebuildViews();
    }
  }

  static JavaStackFrame getStackFrame(AnActionEvent e) {
    return getSelectedStackFrame(e);
  }

  static StackFrameProxyImpl getStackFrameProxy(AnActionEvent e) {
    JavaStackFrame stackFrame = getSelectedStackFrame(e);
    return stackFrame != null ? stackFrame.getStackFrameProxy() : null;
  }

  private static @Nullable JavaStackFrame getSelectedStackFrame(AnActionEvent e) {
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
}
