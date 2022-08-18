// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.refactoring.JavaRefactoringFactory;
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

  public MoveToPackageFix(@NotNull PsiFile psiFile, @NotNull String targetPackage) {
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

  public static MoveToPackageFix createIfAvailable(@Nullable PsiFile myFile, @Nullable String targetPackage) {
    if (myFile == null
        || !myFile.isValid()
        || !myFile.getManager().isInProject(myFile)
        || !(myFile instanceof PsiJavaFile)
        || ((PsiJavaFile)myFile).getClasses().length == 0
        || targetPackage == null) {
      return null;
    }
    return new MoveToPackageFix(myFile, targetPackage);
  }

  @Override
  public void invoke(@NotNull Project project,
                     @NotNull PsiFile file,
                     @Nullable Editor editor,
                     @NotNull PsiElement startElement,
                     @NotNull PsiElement endElement) {
    PsiFile myFile = startElement.getContainingFile();

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
      SingleSourceRootMoveDestination moveDestination =
        new SingleSourceRootMoveDestination(PackageWrapper.create(JavaDirectoryService.getInstance().getPackage(directory)), directory);
      JavaRefactoringFactory.getInstance(project)
        .createMoveClassesOrPackages(((PsiJavaFile) myFile).getClasses(), moveDestination, false, false).run();
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
