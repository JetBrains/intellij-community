// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.debugger.impl.shared.actions;

import com.intellij.configurationStore.StoreUtilKt;
import com.intellij.debugger.settings.DebuggerSettings;
import com.intellij.java.debugger.impl.shared.SharedJavaDebuggerSession;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy;
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree;
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState;
import org.jetbrains.annotations.NotNull;

class ShowTypesAction extends DumbAwareToggleAction implements ActionRemoteBehaviorSpecification.FrontendOtherwiseBackend {
  @Override
  public boolean isSelected(@NotNull AnActionEvent e) {
    return DebuggerSettings.getInstance().SHOW_TYPES;
  }

  @Override
  public void setSelected(@NotNull AnActionEvent e, boolean state) {
    DebuggerSettings.getInstance().SHOW_TYPES = state;
    StoreUtilKt.saveSettingsForRemoteDevelopment(ApplicationManager.getApplication());
    Project project = e.getProject();
    if (project != null) {
      if (DebuggerUIUtil.isInDetachedTree(e)) {
        XDebuggerTree tree = XDebuggerTree.getTree(e);
        if (tree != null) {
          tree.rebuildAndRestore(XDebuggerTreeState.saveState(tree));
        }
      }
      XDebugSessionProxy session = DebuggerUIUtil.getSessionProxy(e);
      if (session != null) {
        session.rebuildViews();
      }
    }
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    Project project = e.getProject();
    if (project != null) {
      SharedJavaDebuggerSession javaSession = SharedJavaDebuggerSession.findSession(e);
      if (javaSession != null) {
        e.getPresentation().setEnabledAndVisible(true);
        super.update(e);
        return;
      }
    }
    e.getPresentation().setEnabledAndVisible(false);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
