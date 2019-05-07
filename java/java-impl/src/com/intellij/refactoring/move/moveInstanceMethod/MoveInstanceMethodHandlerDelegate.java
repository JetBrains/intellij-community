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
package com.intellij.refactoring.move.moveInstanceMethod;

import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.moveClassesOrPackages.JavaMoveClassesOrPackagesHandler;
import org.jetbrains.annotations.Nullable;

public class MoveInstanceMethodHandlerDelegate extends MoveHandlerDelegate {
  @Override
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    if (elements.length != 1) return false;
    PsiElement element = elements [0];
    if (!(element instanceof PsiMethod)) return false;
    if (element instanceof SyntheticElement) return false;
    PsiMethod method = (PsiMethod) element;
    if (method.hasModifierProperty(PsiModifier.STATIC)) return false;
    return targetContainer == null || super.canMove(elements, targetContainer);
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
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (!method.hasModifierProperty(PsiModifier.STATIC))  {
        new MoveInstanceMethodHandler().invoke(project, new PsiElement[]{method}, dataContext);
        return true;
      }
    }
    return false;
  }

  @Override
  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    new MoveInstanceMethodHandler().invoke(project, elements, null);
  }
}
