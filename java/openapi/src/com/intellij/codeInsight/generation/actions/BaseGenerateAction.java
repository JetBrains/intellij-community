// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInsight.generation.actions;

import com.intellij.codeInsight.CodeInsightActionHandler;
import com.intellij.codeInsight.actions.CodeInsightAction;
import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class BaseGenerateAction extends CodeInsightAction implements GenerateActionPopupTemplateInjector {
  private final CodeInsightActionHandler myHandler;

  protected BaseGenerateAction(CodeInsightActionHandler handler) {
    myHandler = handler;
  }

  @Override
  protected void update(@NotNull Presentation presentation,
                        @NotNull Project project,
                        @NotNull Editor editor,
                        @NotNull PsiFile psiFile,
                        @NotNull DataContext dataContext,
                        @Nullable String actionPlace) {
    super.update(presentation, project, editor, psiFile, dataContext, actionPlace);
    if (myHandler instanceof ContextAwareActionHandler && presentation.isEnabled()) {
      presentation.setEnabled(((ContextAwareActionHandler)myHandler).isAvailableForQuickList(editor, psiFile, dataContext));
    }
  }

  @Override
  protected void update(@NotNull Presentation presentation, @NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    presentation.setEnabledAndVisible(isValidForFile(project, editor, psiFile));
  }

  @Override
  public @Nullable AnAction createEditTemplateAction(DataContext dataContext) {
    return null;
  }

  @Override
  protected final @NotNull CodeInsightActionHandler getHandler() {
    return myHandler;
  }

  protected @Nullable PsiClass getTargetClass (Editor editor, PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    final PsiClass target = PsiTreeUtil.getParentOfType(element, PsiClass.class);
    return target instanceof SyntheticElement ? null : target;
  }

  @Override
  protected boolean isValidForFile(@NotNull Project project, @NotNull Editor editor, @NotNull PsiFile psiFile) {
    if (!(psiFile instanceof PsiJavaFile)) return false;
    if (psiFile instanceof PsiCompiledElement) return false;

    PsiClass targetClass = getTargetClass(editor, psiFile);
    return targetClass != null && isValidForClass(targetClass);
  }

  protected boolean isValidForClass(final PsiClass targetClass) {
    return !targetClass.isInterface();
  }
}
