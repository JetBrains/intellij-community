// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hint.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.BaseCodeInsightAction;
import com.intellij.codeInsight.hint.ShowContainerInfoHandler;
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Context info action
 */
@ApiStatus.Internal
public final class ShowContainerInfoAction extends BaseCodeInsightAction{
  @Override
  protected @NotNull CodeInsightActionHandler getHandler() {
    return new ShowContainerInfoHandler();
  }

  @Override
  protected @Nullable Editor getBaseEditor(final @NotNull DataContext dataContext, final @NotNull Project project) {
    return CommonDataKeys.EDITOR_EVEN_IF_INACTIVE.getData(dataContext);
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, final @NotNull PsiFile file) {
    return LanguageStructureViewBuilder.getInstance().getStructureViewBuilder(file) instanceof TreeBasedStructureViewBuilder;
  }
}