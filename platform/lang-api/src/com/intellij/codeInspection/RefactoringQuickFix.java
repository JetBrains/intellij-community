// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection;

import com.intellij.ide.DataManager;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.refactoring.RefactoringActionHandler;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;

/**
 * @author Bas Leijdekkers
 */
public interface RefactoringQuickFix extends LocalQuickFix {

  @Override
  default boolean startInWriteAction() {
    return false;
  }

  /**
   * Usually a call to com.intellij.refactoring.RefactoringActionHandlerFactory or a language specific factory like
   * com.intellij.refactoring.JavaRefactoringActionHandlerFactory.
   */
  @NotNull
  RefactoringActionHandler getHandler();

  /**
   * Override if preferred handler can be chosen based on context
   */
  default @NotNull RefactoringActionHandler getHandler(@NotNull DataContext context) {
    return getHandler();
  }

  default PsiElement getElementToRefactor(PsiElement element) {
    final PsiElement parent = element.getParent();
    return (parent instanceof PsiNamedElement) ? parent : element;
  }

  default void doFix(@NotNull PsiElement element) {
    final PsiElement elementToRefactor = getElementToRefactor(element);
    if (elementToRefactor == null) {
      return;
    }
    final Consumer<DataContext> consumer = dataContext -> {
      dataContext = enhanceDataContext(dataContext);
      final RefactoringActionHandler handler = getHandler(dataContext);
      handler.invoke(element.getProject(), new PsiElement[] {elementToRefactor}, dataContext);
    };
    DataManager.getInstance().getDataContextFromFocusAsync().onSuccess(consumer);
  }

  @Override
  default void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    doFix(descriptor.getPsiElement());
  }

  /**
   * @see com.intellij.openapi.actionSystem.impl.SimpleDataContext
   */
  default @NotNull DataContext enhanceDataContext(@NonNls DataContext context) {
    return context;
  }
}
