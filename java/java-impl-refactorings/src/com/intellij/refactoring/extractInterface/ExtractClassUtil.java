// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.extractInterface;

import com.intellij.java.refactoring.JavaRefactoringBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;
import com.intellij.refactoring.ui.YesNoPreviewUsagesDialog;
import org.jetbrains.annotations.NotNull;

/**
 * @author dsl
 */
public final class ExtractClassUtil {
  public static void askAndTurnRefsToSuper(final Project project,
                                           final SmartPsiElementPointer classPointer,
                                           final SmartPsiElementPointer interfacePointer) {
    final PsiElement classElement = classPointer.getElement();
    final PsiElement interfaceElement = interfacePointer.getElement();
    if (classElement instanceof PsiClass && interfaceElement instanceof PsiClass) {
      askAndTurnRefsToSuper((PsiClass)classElement, (PsiClass)interfaceElement);
    }
  }

  public static void askAndTurnRefsToSuper(@NotNull PsiClass subClass, @NotNull PsiClass superClass) {
    String superClassName = superClass.getName();
    String className = (subClass).getName();
    String createdString = superClass.isInterface() ?
                           JavaRefactoringBundle.message("interface.has.been.successfully.created", superClassName) :
                           JavaRefactoringBundle.message("class.has.been.successfully.created", superClassName);
    String message = createdString + "\n" +
                     JavaRefactoringBundle.message("use.super.references.prompt",
                                               ApplicationNamesInfo.getInstance().getProductName(), className, superClassName);
    YesNoPreviewUsagesDialog dialog = new YesNoPreviewUsagesDialog(
      JavaRefactoringBundle.message("analyze.and.replace.usages"),
      message,
      JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_PREVIEW_USAGES,
      /*HelpID.TURN_REFS_TO_SUPER*/null, subClass.getProject());
    if (dialog.showAndGet()) {
      final boolean isPreviewUsages = dialog.isPreviewUsages();
      JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_PREVIEW_USAGES = isPreviewUsages;
      TurnRefsToSuperProcessor processor =
        new TurnRefsToSuperProcessor(subClass.getProject(), subClass, superClass, false);
      processor.setPreviewUsages(isPreviewUsages);
      processor.run();
    }
  }
}
