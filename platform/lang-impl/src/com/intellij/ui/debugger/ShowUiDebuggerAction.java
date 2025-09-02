// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.ui.debugger;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import org.jetbrains.annotations.NotNull;

final class ShowUiDebuggerAction extends DumbAwareAction {

  private UiDebugger myDebugger;

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setText(IdeBundle.messagePointer("action.presentation.ShowUiDebuggerAction.text"));
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    if (myDebugger == null) {
      myDebugger = new UiDebugger() {
        @Override
        public void dispose() {
          super.dispose();
          myDebugger = null;
        }
      };
    } else {
      myDebugger.show();
    }
  }
}