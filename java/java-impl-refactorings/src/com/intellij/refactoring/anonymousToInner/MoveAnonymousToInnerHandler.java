// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.anonymousToInner;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.Language;
import com.intellij.lang.jvm.JvmLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNewExpression;
import com.intellij.psi.PsiReference;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public class MoveAnonymousToInnerHandler extends MoveHandlerDelegate {
  @Override
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer, @Nullable PsiReference reference) {
    for (PsiElement element : elements) {
      if (!(element instanceof PsiAnonymousClass)) return false;
    }
    return targetContainer == null || super.canMove(elements, targetContainer, reference);
  }

  @Override
  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (element instanceof PsiAnonymousClass && element.getParent() instanceof PsiNewExpression) {
      new AnonymousToInnerHandler().invoke(project, editor, (PsiAnonymousClass)element);
      return true;
    }
    return false;
  }

  @Override
  public boolean supportsLanguage(@NotNull Language language) {
    return language instanceof JvmLanguage;
  }

  @Nullable
  @Override
  public String getActionName(PsiElement @NotNull [] elements) {
    return JavaRefactoringBundle.message("convert.anonymous.to.inner.action.name");
  }
}
