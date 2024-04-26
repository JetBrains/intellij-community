// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.anonymousToInner;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.Language;
import com.intellij.lang.jvm.JvmLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class MoveAnonymousToInnerHandler extends MoveHandlerDelegate {
  @Override
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer, @Nullable PsiReference reference) {
    for (PsiElement element : elements) {
      boolean anonymous = element instanceof PsiAnonymousClass;
      boolean local = element instanceof PsiClass cls && PsiUtil.isLocalClass(cls);
      if (!anonymous && !local) return false;
    }
    return targetContainer == null || super.canMove(elements, targetContainer, reference);
  }

  @Override
  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (element instanceof PsiAnonymousClass anonymousClass && element.getParent() instanceof PsiNewExpression) {
      new AnonymousToInnerHandler().invoke(project, editor, anonymousClass);
      return true;
    }
    if (element instanceof PsiClass localClass && PsiUtil.isLocalClass(localClass)) {
      new AnonymousToInnerHandler().invoke(project, editor, localClass);
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
    if (elements.length > 0 && elements[0] instanceof PsiAnonymousClass) {
      return JavaRefactoringBundle.message("convert.anonymous.to.inner.action.name");
    }
    return JavaRefactoringBundle.message("convert.local.to.inner.action.name");
  }
}
