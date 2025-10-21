// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.actions;

import com.intellij.debugger.DebuggerManagerEx;
import com.intellij.debugger.impl.DebuggerSession;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.debugger.ui.HotSwapUI;
import com.intellij.debugger.ui.HotSwapUIImpl;
import com.intellij.idea.ActionsBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.hotswap.HotSwapStatistics;
import com.intellij.xdebugger.impl.rpc.HotSwapSource;
import org.jetbrains.annotations.NotNull;

public class HotSwapAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) return;
    DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    DebuggerSession session = debuggerManager.getContext().getDebuggerSession();

    if (session != null && session.isAttached()) {
      HotSwapStatistics.logHotSwapCalled(project, HotSwapSource.RELOAD_ALL);
      HotSwapUI.getInstance(project).reloadChangedClasses(session, DebuggerSettings.getInstance().COMPILE_BEFORE_HOTSWAP);
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project == null) {
      e.getPresentation().setEnabled(false);
      return;
    }

    DebuggerManagerEx debuggerManager = DebuggerManagerEx.getInstanceEx(project);
    DebuggerSession session = debuggerManager.getContext().getDebuggerSession();

    e.getPresentation().setEnabled(session != null && HotSwapUIImpl.canHotSwap(session));
    boolean compile = DebuggerSettings.getInstance().COMPILE_BEFORE_HOTSWAP;
    String text = compile ? ActionsBundle.message("action.Hotswap.and.compile.text")
                          : ActionsBundle.message("action.Hotswap.text");
    String description = compile ? ActionsBundle.message("action.Hotswap.and.compile.description")
                                 : ActionsBundle.message("action.Hotswap.description");
    e.getPresentation().setText(text);
    e.getPresentation().setDescription(description);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
