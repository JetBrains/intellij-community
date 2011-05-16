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
package com.intellij.codeInspection;

import com.intellij.CommonBundle;
import com.intellij.codeInsight.CodeInsightUtilBase;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.ide.util.PackageUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class MoveToPackageFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance("#com.intellij.codeInspection.MoveToPackageFix");
  private final String myTargetPackage;

  public MoveToPackageFix(String targetPackage) {
    myTargetPackage = targetPackage;
  }

  @NotNull
  public String getName() {
    return QuickFixBundle.message("move.class.to.package.text", myTargetPackage);
  }

  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("move.class.to.package.family");
  }

  public boolean isAvailable(PsiFile myFile) {
    return myFile != null
        && myFile.isValid()
        && myFile.getManager().isInProject(myFile)
        && myFile instanceof PsiJavaFile
        && ((PsiJavaFile) myFile).getClasses().length != 0
        && myTargetPackage != null;
  }

  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element == null) return;
    final PsiFile myFile = element.getContainingFile();

    if (!CodeInsightUtilBase.prepareFileForWrite(myFile)) return;

    try {
      String packageName = myTargetPackage;
      PsiDirectory directory = PackageUtil.findOrCreateDirectoryForPackage(project, packageName, null, true);

      if (directory == null) {
        return;
      }
      String error = RefactoringMessageUtil.checkCanCreateFile(directory, myFile.getName());
      if (error != null) {
        Messages.showMessageDialog(project, error, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return;
      }
      new MoveClassesOrPackagesProcessor(
              project,
              new PsiElement[]{((PsiJavaFile) myFile).getClasses()[0]},
              new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(directory)), directory), false,
              false,
              null).run();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  public boolean startInWriteAction() {
    return false;
  }


}
