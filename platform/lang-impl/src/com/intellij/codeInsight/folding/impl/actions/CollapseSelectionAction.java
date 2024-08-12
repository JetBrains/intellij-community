// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.folding.impl.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.folding.impl.CollapseSelectionHandler;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class CollapseSelectionAction extends BaseCodeInsightAction implements DumbAware {
  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new CollapseSelectionHandler();
  }

  @Override
  public void update(@NotNull AnActionEvent event) {
    super.update(event);
    event.getPresentation().setVisible(event.getPresentation().isVisible() && !EditorUtil.contextMenuInvokedOutsideOfSelection(event));
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile file) {
    return CollapseSelectionHandler.isEnabled(editor);
  }
}
