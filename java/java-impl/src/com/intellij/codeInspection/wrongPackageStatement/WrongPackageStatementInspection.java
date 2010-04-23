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

import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.daemon.JavaErrorMessages;
import com.intellij.codeInspection.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 14-Nov-2005
 */
public class WrongPackageStatementInspection extends BaseJavaLocalInspectionTool {
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    // does not work in tests since CodeInsightTestCase copies file into temporary location
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    if (file instanceof PsiJavaFile) {
      if (JspPsiUtil.isInJspFile(file)) return null;
      PsiJavaFile javaFile = (PsiJavaFile)file;

      PsiDirectory directory = javaFile.getContainingDirectory();
      if (directory == null) return null;
      PsiPackage dirPackage = JavaDirectoryService.getInstance().getPackage(directory);
      if (dirPackage == null) return null;
      PsiPackageStatement packageStatement = javaFile.getPackageStatement();

      // highlight the first class in the file only
      PsiClass[] classes = javaFile.getClasses();
      if (classes.length == 0 && packageStatement == null) return null;

      String packageName = dirPackage.getQualifiedName();
      if (!Comparing.strEqual(packageName, "", true) && packageStatement == null) {
        String description = JavaErrorMessages.message("missing.package.statement", packageName);

        return new ProblemDescriptor[]{manager.createProblemDescriptor(classes[0].getNameIdentifier(), description,
                                                                       new AdjustPackageNameFix(javaFile, null, dirPackage),
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)};
      }
      if (packageStatement != null) {
        final PsiJavaCodeReferenceElement packageReference = packageStatement.getPackageReference();
        PsiPackage classPackage = (PsiPackage)packageReference.resolve();
        List<LocalQuickFix> availableFixes = new ArrayList<LocalQuickFix>();
        if (classPackage == null) {
          availableFixes.add(new AdjustPackageNameFix(javaFile, packageStatement, dirPackage));
        }
        else if (!Comparing.equal(dirPackage.getQualifiedName(), packageReference.getText(), true)) {
          availableFixes.add(new AdjustPackageNameFix(javaFile, packageStatement, dirPackage));
          MoveToPackageFix moveToPackageFix = new MoveToPackageFix(file, classPackage);
          if (moveToPackageFix.isAvailable()) {
            availableFixes.add(moveToPackageFix);
          }
        }
        if (!availableFixes.isEmpty()){
          String description = JavaErrorMessages.message("package.name.file.path.mismatch",
                                                         packageReference.getText(),
                                                         dirPackage.getQualifiedName());
          return new ProblemDescriptor[]{manager.createProblemDescriptor(packageStatement.getPackageReference(), description, isOnTheFly, availableFixes.toArray(new LocalQuickFix[availableFixes.size()]), ProblemHighlightType.GENERIC_ERROR_OR_WARNING)};

        }
      }
    }
    return null;
  }

  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("wrong.package.statement");
  }

  @NotNull
  @NonNls
  public String getShortName() {
    return "WrongPackageStatement";
  }

  public boolean isEnabledByDefault() {
    return true;
  }
}
