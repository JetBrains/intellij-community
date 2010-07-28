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
package com.intellij.refactoring.move.moveInner;

import com.intellij.featureStatistics.FeatureUsageTracker;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.move.MoveHandlerDelegate;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesHandlerBase;
import com.intellij.refactoring.util.CommonRefactoringUtil;
import org.jetbrains.annotations.Nullable;

public class MoveInnerToUpperHandler extends MoveHandlerDelegate {
  public boolean canMove(final PsiElement[] elements, @Nullable final PsiElement targetContainer) {
    if (elements.length != 1) return false;
    PsiElement element = elements [0];
    return isNonStaticInnerClass(element) &&
           (targetContainer == null || targetContainer.equals(MoveInnerImpl.getTargetContainer((PsiClass)elements[0], false)));
                                                                        
  }

  private static boolean isNonStaticInnerClass(final PsiElement element) {
    return element instanceof PsiClass && element.getParent() instanceof PsiClass &&
           !((PsiClass) element).hasModifierProperty(PsiModifier.STATIC);
  }

  public void doMove(final Project project, final PsiElement[] elements, final PsiElement targetContainer, final MoveCallback callback) {
    MoveInnerImpl.doMove(project, elements, callback);
  }

  public boolean tryToMove(final PsiElement element, final Project project, final DataContext dataContext, final PsiReference reference,
                           final Editor editor) {
    if (isNonStaticInnerClass(element) && !MoveClassesOrPackagesHandlerBase.isReferenceInAnonymousClass(reference)) {
      PsiClass aClass = (PsiClass) element;
      FeatureUsageTracker.getInstance().triggerFeatureUsed("refactoring.move.moveInner");
      final PsiClass containingClass = aClass.getContainingClass();
      if (containingClass instanceof JspClass) {
        CommonRefactoringUtil.showErrorHint(project, editor, RefactoringBundle.message("move.nonstatic.class.from.jsp.not.supported"),
                                            RefactoringBundle.message("move.title"), null);
        return true;
      }
      MoveInnerImpl.doMove(project, new PsiElement[]{aClass}, null);
      return true;
    }
    return false;
  }
}
