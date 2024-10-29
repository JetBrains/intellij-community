// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.navigation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.navigation.MethodDownHandler;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public final class MethodDownAction extends BaseCodeInsightAction implements DumbAware {
  public MethodDownAction() {
    setEnabledInModalContext(true);
  }

  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new MethodDownHandler();
  }

  @Override
  protected boolean isValidForLookup() {
    return true;
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, final @NotNull PsiFile file) {
    return MethodUpAction.checkValidForFile(file);
  }
}