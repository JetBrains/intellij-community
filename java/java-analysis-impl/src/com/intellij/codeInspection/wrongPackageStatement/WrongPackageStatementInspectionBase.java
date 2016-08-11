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
import com.intellij.psi.util.FileTypeUtils;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * User: anna
 * Date: 14-Nov-2005
 */
public class WrongPackageStatementInspectionBase extends BaseJavaBatchLocalInspectionTool {
  @Override
  @Nullable
  public ProblemDescriptor[] checkFile(@NotNull PsiFile file, @NotNull InspectionManager manager, boolean isOnTheFly) {
    // does not work in tests since CodeInsightTestCase copies file into temporary location
    if (ApplicationManager.getApplication().isUnitTestMode()) return null;
    if (file instanceof PsiJavaFile) {
      if (FileTypeUtils.isInServerPageFile(file)) return null;
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
                                                                       new AdjustPackageNameFix(packageName),
                                                                       ProblemHighlightType.GENERIC_ERROR_OR_WARNING, isOnTheFly)};
      }
      if (packageStatement != null) {
        final PsiJavaCodeReferenceElement packageReference = packageStatement.getPackageReference();
        PsiPackage classPackage = (PsiPackage)packageReference.resolve();
        List<LocalQuickFix> availableFixes = new ArrayList<>();
        if (classPackage == null || !Comparing.equal(dirPackage.getQualifiedName(), packageReference.getQualifiedName(), true)) {
          availableFixes.add(new AdjustPackageNameFix(packageName));
          String packName = classPackage != null ? classPackage.getQualifiedName() : packageReference.getQualifiedName();
          addMoveToPackageFix(file, packName, availableFixes);
        }
        if (!availableFixes.isEmpty()){
          String description = JavaErrorMessages.message("package.name.file.path.mismatch",
                                                         packageReference.getQualifiedName(),
                                                         dirPackage.getQualifiedName());
          LocalQuickFix[] fixes = availableFixes.toArray(new LocalQuickFix[availableFixes.size()]);
          ProblemDescriptor descriptor =
            manager.createProblemDescriptor(packageStatement.getPackageReference(), description, isOnTheFly,
                                            fixes, ProblemHighlightType.GENERIC_ERROR_OR_WARNING);
          return new ProblemDescriptor[]{descriptor};

        }
      }
    }
    return null;
  }

  protected void addMoveToPackageFix(PsiFile file, String packName, List<LocalQuickFix> availableFixes) {
  }

  @Override
  @NotNull
  public String getGroupDisplayName() {
    return "";
  }

  @Override
  @NotNull
  public HighlightDisplayLevel getDefaultLevel() {
    return HighlightDisplayLevel.ERROR;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return InspectionsBundle.message("wrong.package.statement");
  }

  @Override
  @NotNull
  @NonNls
  public String getShortName() {
    return "WrongPackageStatement";
  }

  @Override
  public boolean isEnabledByDefault() {
    return true;
  }
}
