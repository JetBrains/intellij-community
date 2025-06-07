// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.wrongPackageStatement;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.SingleFileSourcesTracker;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class AdjustPackageNameFix extends ModCommandQuickFix {
  private final String myName;

  public AdjustPackageNameFix(String targetPackage) {
    myName = targetPackage;
  }

  @Override
  public @NotNull String getName() {
    return QuickFixBundle.message("adjust.package.text", myName);
  }

  @Override
  public @NotNull String getFamilyName() {
    return QuickFixBundle.message("adjust.package.family");
  }

  @Override
  public final @NotNull ModCommand perform(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getStartElement();
    PsiFile origFile = element.getContainingFile();
    PsiDirectory directory = origFile.getContainingDirectory();
    if (directory == null) return ModCommand.nop();
    return ModCommand.psiUpdate(element, (e, updater) -> applyFix(e, origFile, directory));
  }

  public static void applyFix(@NotNull PsiElement element, @NotNull PsiFile origFile, @NotNull PsiDirectory directory) {
    PsiFile file = element.getContainingFile();
    PsiPackage myTargetPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (myTargetPackage == null) return;
    String myTargetPackageName = myTargetPackage.getQualifiedName();
    SingleFileSourcesTracker singleFileSourcesTracker = SingleFileSourcesTracker.getInstance(file.getProject());
    String singleFileSourcePackageName = singleFileSourcesTracker.getPackageNameForSingleFileSource(origFile.getVirtualFile());
    if (singleFileSourcePackageName != null) myTargetPackageName = singleFileSourcePackageName;

    PsiPackageStatement statement = ((PsiJavaFile)file).getPackageStatement();

    if (myTargetPackageName.isEmpty()) {
      if (statement != null) {
        statement.delete();
      }
    }
    else {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(file.getProject());
      final PsiPackageStatement packageStatement = factory.createPackageStatement(myTargetPackageName);
      if (statement != null) {
        statement.getPackageReference().replace(packageStatement.getPackageReference());
      }
      else {
        file.addAfter(packageStatement, null);
      }
    }
  }
}
