// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.refactoring.actions;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.TitledHandler;
import com.intellij.lang.Language;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.lang.ExtractIncludeFileBase;
import com.intellij.refactoring.lang.LanguageExtractInclude;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ExtractIncludeAction extends BasePlatformRefactoringAction {
  @Override
  public boolean isAvailableInEditorOnly() {
    return true;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    return false;
  }

  @Override
  protected boolean isAvailableForLanguage(Language language) {
    return true;
  }

  @Override
  public void update(final @NotNull AnActionEvent e) {
    super.update(e);
    final RefactoringActionHandler handler = getHandler(e.getDataContext());
    if (handler instanceof TitledHandler) {
      e.getPresentation().setText(((TitledHandler) handler).getActionTitle());
    }
    else {
      e.getPresentation().setText(IdeBundle.messagePointer("action.presentation.ExtractIncludeAction.text"));
    }
  }

  @Override
  protected boolean isAvailableForFile(PsiFile file) {
    final RefactoringActionHandler handler = LanguageExtractInclude.INSTANCE.forLanguage(file.getViewProvider().getBaseLanguage());
    if (handler instanceof ExtractIncludeFileBase<?>) {
      return ((ExtractIncludeFileBase<?>)handler).isAvailableForFile(file);
    }
    return handler != null;
  }

  @Override
  protected @Nullable RefactoringActionHandler getHandler(@NotNull Language language, PsiElement element) {
    RefactoringActionHandler handler = super.getHandler(language, element);
    if (handler != null) return handler;
    return element == null ? null : getHandler(element);
  }

  @Override
  protected @Nullable RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider provider) {
    return null;
  }

  @Override
  protected @Nullable RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider provider, PsiElement element) {
    return getHandler(element);
  }

  private static @Nullable RefactoringActionHandler getHandler(@NotNull PsiElement element) {
    PsiFile file = element.getContainingFile();
    if (file == null) return null;
    return LanguageExtractInclude.INSTANCE.forLanguage(file.getViewProvider().getBaseLanguage());
  }
}
