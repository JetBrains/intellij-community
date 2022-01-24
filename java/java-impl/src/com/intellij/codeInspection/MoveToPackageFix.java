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
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.*;
import com.intellij.refactoring.JavaSpecialRefactoringProvider;
import com.intellij.refactoring.PackageWrapper;
import com.intellij.refactoring.move.moveClassesOrPackages.SingleSourceRootMoveDestination;
import com.intellij.refactoring.util.CommonMoveClassesOrPackagesUtil;
import com.intellij.refactoring.util.RefactoringMessageUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MoveToPackageFix extends LocalQuickFixAndIntentionActionOnPsiElement {
  private static final Logger LOG = Logger.getInstance(MoveToPackageFix.class);
  private final String myTargetPackage;

  public MoveToPackageFix(PsiFile psiFile, String targetPackage) {
    super(psiFile);
    myTargetPackage = targetPackage;
  }

  @Override
  public @IntentionName @NotNull String getText() {
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
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    final PsiFile myFile = startElement.getContainingFile();

    if (!FileModificationService.getInstance().prepareFileForWrite(myFile)) return;

    try {
      String error;
      PsiDirectory directory = null;
      try {
        directory = CommonMoveClassesOrPackagesUtil.chooseDestinationPackage(project, myTargetPackage, myFile.getContainingDirectory());

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
      JavaSpecialRefactoringProvider.getInstance().moveClassesOrPackages(
        project,
        ((PsiJavaFile) myFile).getClasses(),
        new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(directory)), directory), false,
        false,
        null
      );
    }
    catch (IncorrectOperationException e) {
      LOG.error(e);
    }
  }

  @Override
  public boolean startInWriteAction() {
    return false;
  }
}
