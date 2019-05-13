/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.CommonRefactoringUtil;

import java.util.HashSet;
import java.util.Set;

public class MoveMembersImpl {
  public static final String REFACTORING_NAME = RefactoringBundle.message("move.members.title");

  /**
   * element should be either not anonymous PsiClass whose members should be moved
   * or PsiMethod of a non-anonymous PsiClass
   * or PsiField of a non-anonymous PsiClass
   * or Inner PsiClass
   */
  public static void doMove(final Project project, PsiElement[] elements, PsiElement targetContainer, MoveCallback moveCallback) {
    if (elements.length == 0) {
      return;
    }

    final PsiClass sourceClass;
    final PsiElement first = elements[0];
    if (first instanceof PsiMember && ((PsiMember)first).getContainingClass() != null) {
      sourceClass = ((PsiMember)first).getContainingClass();
    } else {
      return;
    }
    
    final Set<PsiMember> preselectMembers = new HashSet<>();
    for (PsiElement element : elements) {
      if (element instanceof PsiMember && !sourceClass.equals(((PsiMember)element).getContainingClass())) {
        String message = RefactoringBundle.getCannotRefactorMessage(
          RefactoringBundle.message("members.to.be.moved.should.belong.to.the.same.class"));
        CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_MEMBERS, project);
        return;
      }
      if (element instanceof PsiField) {
        PsiField field = (PsiField)element;
        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
          String fieldName = PsiFormatUtil.formatVariable(
            field,
            PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_TYPE | PsiFormatUtil.TYPE_AFTER,
            PsiSubstitutor.EMPTY);
          String message = RefactoringBundle.message("field.0.is.not.static", fieldName,
                                                          REFACTORING_NAME);
          CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_MEMBERS, project);
          return;
        }
        preselectMembers.add(field);
      }
      else if (element instanceof PsiMethod) {
        PsiMethod method = (PsiMethod)element;
        String methodName = PsiFormatUtil.formatMethod(
          method,
          PsiSubstitutor.EMPTY, PsiFormatUtil.SHOW_NAME | PsiFormatUtil.SHOW_PARAMETERS,
          PsiFormatUtil.SHOW_TYPE
        );
        if (method.isConstructor()) {
          String message = RefactoringBundle.message("0.refactoring.cannot.be.applied.to.constructors", REFACTORING_NAME);
          CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_MEMBERS, project);
          return;
        }
        if (!method.hasModifierProperty(PsiModifier.STATIC)) {
          String message = RefactoringBundle.message("method.0.is.not.static", methodName,
                                                REFACTORING_NAME);
          CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_MEMBERS, project);
          return;
        }
        preselectMembers.add(method);
      }
      else if (element instanceof PsiClass) {
        PsiClass aClass = (PsiClass)element;
        if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
          String message = RefactoringBundle.message("inner.class.0.is.not.static", aClass.getQualifiedName(),
                                                REFACTORING_NAME);
          CommonRefactoringUtil.showErrorMessage(REFACTORING_NAME, message, HelpID.MOVE_MEMBERS, project);
          return;
        }
        preselectMembers.add(aClass);
      }
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, sourceClass)) return;

    final PsiClass initialTargerClass = targetContainer instanceof PsiClass? (PsiClass) targetContainer : null;

    MoveMembersDialog dialog = new MoveMembersDialog(
            project,
            sourceClass,
            initialTargerClass,
            preselectMembers,
            moveCallback);
    dialog.show();
  }
}
