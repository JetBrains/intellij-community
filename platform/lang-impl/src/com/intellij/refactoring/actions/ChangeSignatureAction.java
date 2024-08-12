// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.actions;

import com.intellij.lang.ContextAwareActionHandler;
import com.intellij.lang.LanguageRefactoringSupport;
import com.intellij.lang.refactoring.RefactoringSupportProvider;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.ScrollType;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiNameIdentifierOwner;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.RefactoringActionHandler;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.changeSignature.ChangeSignatureHandler;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class ChangeSignatureAction extends BasePlatformRefactoringAction {

  public ChangeSignatureAction() {
    setInjectedContext(true);
  }

  @Override
  public boolean isAvailableInEditorOnly() {
    return false;
  }

  @Override
  public boolean isEnabledOnElements(PsiElement @NotNull [] elements) {
    if (elements.length == 1) {
      PsiElement member = findTargetMember(elements[0]);
      return member != null && getChangeSignatureHandler(member) != null;
    }
    return false;
  }

  @Override
  protected boolean isAvailableOnElementInEditorAndFile(final @NotNull PsiElement element, final @NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext context) {
    PsiElement targetMember = findTargetMember(element);
    if (targetMember == null) {
      final ChangeSignatureHandler targetHandler = getChangeSignatureHandler(element);
      if (targetHandler != null) {
        return true;
      }
      return false;
    }
    final ChangeSignatureHandler targetHandler = getChangeSignatureHandler(targetMember);
    if (targetHandler == null) return false;
    return true;
  }

  private static @Nullable PsiElement findTargetMember(@Nullable PsiElement element) {
    if (element == null) return null;
    final ChangeSignatureHandler fileHandler = getChangeSignatureHandler(element);
    if (fileHandler != null) {
      final PsiElement targetMember = fileHandler.findTargetMember(element);
      if (targetMember != null) return targetMember;
    }
    PsiReference reference = element.getReference();
    if (reference == null && element instanceof PsiNameIdentifierOwner) {
      return element;
    }
    if (reference != null) {
      return reference.resolve();
    }
    return null;
  }

  @Override
  protected @Nullable RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider provider) {
    return provider.getChangeSignatureHandler();
  }

  @Override
  protected @Nullable RefactoringActionHandler getRefactoringHandler(@NotNull RefactoringSupportProvider provider, final PsiElement element) {
    abstract class ContextAwareChangeSignatureHandler implements RefactoringActionHandler, ContextAwareActionHandler {}

    return new ContextAwareChangeSignatureHandler() {
      @Override
      public boolean isAvailableForQuickList(@NotNull Editor editor, @NotNull PsiFile file, @NotNull DataContext dataContext) {
        return findTargetMember(element) != null;
      }

      @Override
      public void invoke(@NotNull Project project, Editor editor, PsiFile file, DataContext dataContext) {
        editor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE);
        final PsiElement targetMember = findTargetMember(element);
        if (targetMember == null) {
          final ChangeSignatureHandler handler = getChangeSignatureHandler(file);
          if (handler != null) {
            final String notFoundMessage = handler.getTargetNotFoundMessage();
            if (notFoundMessage != null) {
              CommonRefactoringUtil.showErrorHint(project, editor, notFoundMessage, RefactoringBundle
                .message("changeSignature.refactoring.name"), null);
            }
          }
          return;
        }
        final ChangeSignatureHandler handler = getChangeSignatureHandler(targetMember);
        if (handler == null) return;
        handler.invoke(project, new PsiElement[]{targetMember}, dataContext);
      }

      @Override
      public void invoke(@NotNull Project project, PsiElement @NotNull [] elements, DataContext dataContext) {
        if (elements.length != 1) return;
        final PsiElement targetMember = findTargetMember(elements[0]);
        if (targetMember == null) return;
        final ChangeSignatureHandler handler = getChangeSignatureHandler(targetMember);
        if (handler == null) return;
        handler.invoke(project, new PsiElement[]{targetMember}, dataContext);
      }
    };
  }

  public static @Nullable ChangeSignatureHandler getChangeSignatureHandler(@NotNull PsiElement language) {
    RefactoringSupportProvider provider = LanguageRefactoringSupport.INSTANCE.forContext(language);
    return provider != null ? provider.getChangeSignatureHandler() : null;
  }
}
