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
package com.intellij.codeInspection.wrongPackageStatement;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

public class AdjustPackageNameFix implements LocalQuickFix {
  private static final Logger LOG = Logger.getInstance(AdjustPackageNameFix.class);
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
  public void applyFix(@NotNull final Project project, @NotNull final ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element == null) return;
    PsiFile myFile = element.getContainingFile();

    PsiDirectory directory = myFile.getContainingDirectory();
    if (directory == null) return;
    PsiPackage myTargetPackage = JavaDirectoryService.getInstance().getPackage(directory);
    if (myTargetPackage == null) return;

    PsiElementFactory factory = JavaPsiFacade.getElementFactory(myFile.getProject());
    PsiPackageStatement myStatement = ((PsiJavaFile)myFile).getPackageStatement();

    if (myTargetPackage.getQualifiedName().isEmpty()) {
      if (myStatement != null) {
        myStatement.delete();
      }
    }
    else {
      final PsiPackageStatement packageStatement = factory.createPackageStatement(myTargetPackage.getQualifiedName());
      if (myStatement != null) {
        myStatement.getPackageReference().replace(packageStatement.getPackageReference());
      }
      else {
        myFile.addAfter(packageStatement, null);
      }
    }
  }
}
