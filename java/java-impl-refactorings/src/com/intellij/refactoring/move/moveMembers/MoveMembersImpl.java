// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.move.moveMembers;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiFormatUtil;
import com.intellij.psi.util.PsiFormatUtilBase;
import com.intellij.refactoring.HelpID;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.move.MoveCallback;
import com.intellij.refactoring.util.CommonRefactoringUtil;

import java.util.HashSet;
import java.util.Set;

public final class MoveMembersImpl {

  /**
   * element should be either not anonymous PsiClass whose members should be moved
   * or PsiMethod of a non-anonymous PsiClass
   * or PsiField of a non-anonymous PsiClass
   * or Inner PsiClass
   */
  public static void doMove(Project project, PsiElement[] elements, PsiElement targetContainer, MoveCallback moveCallback) {
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
      if (element instanceof PsiMember member) {
        preselectMembers.add(member);
        if (!sourceClass.equals(member.getContainingClass())) {
          String message = RefactoringBundle.getCannotRefactorMessage(
            RefactoringBundle.message("members.to.be.moved.should.belong.to.the.same.class"));
          CommonRefactoringUtil.showErrorMessage(getRefactoringName(), message, HelpID.MOVE_MEMBERS, project);
          return;
        }
      }
      if (element instanceof PsiField field) {
        if (!field.hasModifierProperty(PsiModifier.STATIC)) {
          String fieldName = PsiFormatUtil.formatVariable(
            field,
            PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_TYPE | PsiFormatUtilBase.TYPE_AFTER,
            PsiSubstitutor.EMPTY);
          String message = RefactoringBundle.message("field.0.is.not.static", fieldName,
                                                     getRefactoringName());
          CommonRefactoringUtil.showErrorMessage(getRefactoringName(), message, HelpID.MOVE_MEMBERS, project);
          return;
        }
      }
      else if (element instanceof PsiMethod method) {
        String methodName = PsiFormatUtil.formatMethod(
          method,
          PsiSubstitutor.EMPTY, PsiFormatUtilBase.SHOW_NAME | PsiFormatUtilBase.SHOW_PARAMETERS,
          PsiFormatUtilBase.SHOW_TYPE
        );
        if (method.isConstructor()) {
          String message = RefactoringBundle.message("0.refactoring.cannot.be.applied.to.constructors", getRefactoringName());
          CommonRefactoringUtil.showErrorMessage(getRefactoringName(), message, HelpID.MOVE_MEMBERS, project);
          return;
        }
        if (!method.hasModifierProperty(PsiModifier.STATIC)) {
          String message = RefactoringBundle.message("method.0.is.not.static", methodName,
                                                     getRefactoringName());
          CommonRefactoringUtil.showErrorMessage(getRefactoringName(), message, HelpID.MOVE_MEMBERS, project);
          return;
        }
      }
      else if (element instanceof PsiClass aClass) {
        if (!aClass.hasModifierProperty(PsiModifier.STATIC)) {
          String message = JavaRefactoringBundle.message("inner.class.0.is.not.static", aClass.getQualifiedName(),
                                                     getRefactoringName());
          CommonRefactoringUtil.showErrorMessage(getRefactoringName(), message, HelpID.MOVE_MEMBERS, project);
          return;
        }
      }
    }

    if (!CommonRefactoringUtil.checkReadOnlyStatus(project, sourceClass)) return;

    final PsiClass initialTargerClass = targetContainer instanceof PsiClass? (PsiClass) targetContainer : null;

    MoveMembersDialog dialog = new MoveMembersDialog(project, sourceClass, initialTargerClass, preselectMembers, moveCallback);
    dialog.show();
  }

  public static @NlsContexts.DialogTitle String getRefactoringName() {
    return RefactoringBundle.message("move.members.title");
  }
}
