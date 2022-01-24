/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveMembersHandler extends MoveHandlerDelegate {
  @Override
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer, @Nullable PsiReference reference) {
    for(PsiElement element: elements) {
      if (!isFieldOrStaticMethod(element)) return false;
    }
    return targetContainer == null || super.canMove(elements, targetContainer, reference);
  }

  @Override
  public boolean isValidTarget(final PsiElement targetElement, PsiElement[] sources) {
    return targetElement instanceof PsiClass && !(targetElement instanceof PsiAnonymousClass);
  }

  @Override
  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    MoveMembersImpl.doMove(project, elements, targetContainer, callback);
  }

  @Override
  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (isFieldOrStaticMethod(element)) {
      MoveMembersImpl.doMove(project, new PsiElement[]{element}, null, null);
      return true;
    }
    return false;
  }

  private static boolean isFieldOrStaticMethod(final PsiElement element) {
    if (element instanceof PsiField) return true;
    if (element instanceof PsiMethod) {
      if (element instanceof SyntheticElement) return false;
      return ((PsiMethod) element).hasModifierProperty(PsiModifier.STATIC);
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
