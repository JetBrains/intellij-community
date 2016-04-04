/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.refactoring.extractInterface;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.refactoring.RefactoringBundle;
import com.intellij.refactoring.turnRefsToSuper.TurnRefsToSuperProcessor;
import com.intellij.refactoring.ui.YesNoPreviewUsagesDialog;

/**
 * @author dsl
 */
public class ExtractClassUtil {
  public static void askAndTurnRefsToSuper(final Project project,
                                           final SmartPsiElementPointer classPointer, 
                                           final SmartPsiElementPointer interfacePointer) {
    final PsiElement classElement = classPointer.getElement();
    final PsiElement interfaceElement = interfacePointer.getElement();
    if (classElement instanceof PsiClass && classElement.isValid() && interfaceElement instanceof PsiClass && interfaceElement.isValid()) {
      final PsiClass superClass = (PsiClass)interfaceElement;
      String superClassName = superClass.getName();
      String className = ((PsiClass)classElement).getName();
      String createdString = superClass.isInterface() ?
                             RefactoringBundle.message("interface.has.been.successfully.created", superClassName) :
                             RefactoringBundle.message("class.has.been.successfully.created", superClassName);
      String message = createdString + "\n" +
                       RefactoringBundle.message("use.super.references.prompt",
                                                 ApplicationNamesInfo.getInstance().getProductName(), className, superClassName);
      YesNoPreviewUsagesDialog dialog = new YesNoPreviewUsagesDialog(
        RefactoringBundle.message("analyze.and.replace.usages"),
        message,
        JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_PREVIEW_USAGES,
        /*HelpID.TURN_REFS_TO_SUPER*/null, project);
      if (dialog.showAndGet()) {
        final boolean isPreviewUsages = dialog.isPreviewUsages();
        JavaRefactoringSettings.getInstance().EXTRACT_INTERFACE_PREVIEW_USAGES = isPreviewUsages;
        TurnRefsToSuperProcessor processor =
          new TurnRefsToSuperProcessor(project, (PsiClass)classElement, superClass, false);
        processor.setPreviewUsages(isPreviewUsages);
        processor.run();
      }
    }
  }
}
