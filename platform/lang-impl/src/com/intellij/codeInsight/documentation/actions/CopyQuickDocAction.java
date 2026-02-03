// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.actions;

import com.intellij.codeInsight.documentation.DocumentationManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

public final class CopyQuickDocAction extends AnAction implements DumbAware, HintManagerImpl.ActionToIgnore {

  public CopyQuickDocAction() {
    setEnabledInModalContext(true);
  }

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String selected = e.getData(DocumentationManager.SELECTED_QUICK_DOC_TEXT);
    if (selected == null || selected.isEmpty()) {
      return;
    }

    CopyPasteManager.getInstance().setContents(new StringSelection(selected));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    String selected = e.getData(DocumentationManager.SELECTED_QUICK_DOC_TEXT);
    e.getPresentation().setEnabledAndVisible(selected != null && !selected.isEmpty());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
