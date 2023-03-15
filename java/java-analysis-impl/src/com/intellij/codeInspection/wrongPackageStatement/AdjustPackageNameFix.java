// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.wrongPackageStatement;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.preview.IntentionPreviewUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class AdjustPackageNameFix implements LocalQuickFix {
  private final String myName;

  public AdjustPackageNameFix(String targetPackage) {
    myName = targetPackage;
  }

  @Override
  @NotNull
  public String getName() {
    return QuickFixBundle.message("adjust.package.text", myName);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("adjust.package.family");
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element == null) return;
    PsiFile file = element.getContainingFile();
    PsiFile originalFile = IntentionPreviewUtils.getOriginalFile(file);
    PsiDirectory directory = originalFile != null ? originalFile.getContainingDirectory() : file.getContainingDirectory();
    if (directory == null) return;
    PsiPackage myTargetPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (myTargetPackage == null) return;

    PsiPackageStatement statement = ((PsiJavaFile)file).getPackageStatement();

    if (myTargetPackage.getQualifiedName().isEmpty()) {
      if (statement != null) {
        statement.delete();
      }
    }
    else {
      PsiElementFactory factory = JavaPsiFacade.getElementFactory(file.getProject());
      final PsiPackageStatement packageStatement = factory.createPackageStatement(myTargetPackage.getQualifiedName());
      if (statement != null) {
        statement.getPackageReference().replace(packageStatement.getPackageReference());
      }
      else {
        file.addAfter(packageStatement, null);
      }
    }
  }
}
