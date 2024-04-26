// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveMembers;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.lang.Language;
import com.intellij.lang.jvm.JvmLanguage;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class MoveMembersHandler extends MoveHandlerDelegate {
  @Override
  public boolean canMove(PsiElement[] elements, @Nullable PsiElement targetContainer, @Nullable PsiReference reference) {
    for(PsiElement element: elements) {
      if (!isMovableMember(element)) return false;
    }
    return targetContainer == null || super.canMove(elements, targetContainer, reference);
  }

  @Override
  public boolean isValidTarget(PsiElement targetElement, PsiElement[] sources) {
    return targetElement instanceof PsiClass && !(targetElement instanceof PsiAnonymousClass);
  }

  @Override
  public void doMove(Project project, PsiElement[] elements, PsiElement targetContainer, MoveCallback callback) {
    MoveMembersImpl.doMove(project, elements, targetContainer, callback);
  }

  @Override
  public boolean tryToMove(PsiElement element, Project project, DataContext dataContext, PsiReference reference, Editor editor) {
    if (isMovableMember(element)) {
      List<PsiElement> elements = CommonRefactoringUtil.findElementsFromCaretsAndSelections(editor, element.getContainingFile(), null,
                                                                                            e -> isMovableMember(e));
      MoveMembersImpl.doMove(project, elements.toArray(PsiElement.EMPTY_ARRAY), null, null);
      return true;
    }
    return false;
  }

  private static boolean isMovableMember(PsiElement element) {
    if (element instanceof PsiMethod || element instanceof PsiField || element instanceof PsiClassInitializer) {
      if (element instanceof SyntheticElement) return false;
      return ((PsiModifierListOwner) element).hasModifierProperty(PsiModifier.STATIC);
    }
    return false;
  }

  @Nullable
  @Override
  public String getActionName(PsiElement @NotNull [] elements) {
    return JavaRefactoringBundle.message("move.members.action.name");
  }

  @Override
  public boolean supportsLanguage(@NotNull Language language) {
    return language instanceof JvmLanguage;
  }
}
