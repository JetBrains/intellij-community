// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.Language;
import com.intellij.lang.java.JavaLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.moveClassesOrPackages.JavaMoveClassesOrPackagesHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MoveInstanceMethodHandlerDelegate extends MoveHandlerDelegate {
  @Override
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer, @Nullable PsiReference reference) {
    if (elements.length != 1) return false;
    PsiElement element = elements [0];
    if (!(element instanceof PsiMethod method)) return false;
    if (element instanceof SyntheticElement) return false;
    if (method.isConstructor()) return false;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    return targetContainer == null || super.canMove(elements, targetContainer, reference);
  }

  @Override
  public boolean isValidTarget(final PsiElement targetElement, PsiElement[] sources) {
    for (PsiElement source : sources) {
      if (JavaMoveClassesOrPackagesHandler.invalid4Move(source)) return false;
    }
    return targetElement instanceof PsiClass && !(targetElement instanceof PsiAnonymousClass);
  }

  @Override
  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (element instanceof PsiMethod method && !method.hasModifierProperty(PsiModifier.STATIC)) {
      new MoveInstanceMethodHandler().invoke(project, new PsiElement[]{method}, dataContext);
      return true;
    }
    return false;
  }

  @Override
  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    new MoveInstanceMethodHandler().invoke(project, elements, null);
  }

  @Nullable
  @Override
  public String getActionName(PsiElement @NotNull [] elements) {
    return JavaRefactoringBundle.message("move.instance.method.delegate.title");
  }

  @Override
  public boolean supportsLanguage(@NotNull Language language) {
    return language == JavaLanguage.INSTANCE;
  }
}
