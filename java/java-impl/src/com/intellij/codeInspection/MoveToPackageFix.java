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
import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesProcessor;
import com.intellij.refactoring.move.moveClassesOrPackages.MoveClassesOrPackagesUtil;
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

  @Override
  @NotNull
  public String getName() {
    return QuickFixBundle.message("move.class.to.package.text", myTargetPackage);
  }

  @Override
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

  @Override
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element == null) return;
    final PsiFile myFile = element.getContainingFile();

    if (!FileModificationService.getInstance().prepareFileForWrite(myFile)) return;

    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        chooseDirectoryAndMove(project, myFile);
      }
    });
  }

  private void chooseDirectoryAndMove(Project project, PsiFile myFile) {
    try {
      String error;
      PsiDirectory directory = null;
      try {
        directory = MoveClassesOrPackagesUtil.chooseDestinationPackage(project, myTargetPackage, myFile.getContainingDirectory());

        if (directory == null) {
          return;
        }

        error = RefactoringMessageUtil.checkCanCreateFile(directory, myFile.getName());
      }
      catch (IncorrectOperationException e) {
        error = e.getLocalizedMessage();
      }

      if (error != null) {
        Messages.showMessageDialog(project, error, CommonBundle.getErrorTitle(), Messages.getErrorIcon());
        return;
      }
      new MoveClassesOrPackagesProcessor(
              project,
              ((PsiJavaFile) myFile).getClasses(),
              new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(directory)), directory), false,
              false,
              null).run();
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }
}
