/*
 * Copyright 2003-2015 Dave Griffith, Bas Leijdekkers
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
package com.intellij.codeInspection.unusedImport;

import com.intellij.codeInspection.*;
import com.intellij.psi.PsiErrorElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.DeleteImportFix;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class UnusedImportInspection extends GlobalSimpleInspectionTool {
  @NonNls
  public static final String SHORT_NAME = "UNUSED_IMPORT";
  public static final String DISPLAY_NAME = InspectionsBundle.message("unused.import.display.name");

  @Override
  public void checkFile(@NotNull PsiFile file,
                        @NotNull InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull GlobalInspectionContext globalContext,
                        @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    if (!(file instanceof PsiJavaFile) || FileTypeUtils.isInServerPageFile(file)) return;
    PsiJavaFile javaFile = (PsiJavaFile)file;
    final ImportsAreUsedVisitor visitor = new ImportsAreUsedVisitor(javaFile);
    javaFile.accept(visitor);
    for (PsiImportStatementBase unusedImportStatement : visitor.getUnusedImportStatements()) {
      if (unusedImportStatement.getImportReference() != null &&
          !(PsiTreeUtil.skipWhitespacesForward(unusedImportStatement) instanceof PsiErrorElement)) {
        problemsHolder.registerProblem(unusedImportStatement,
                                       InspectionGadgetsBundle.message("unused.import.problem.descriptor"),
                                       new DeleteImportFix());
      }
    }
  }

  @NotNull
  @Override
  public String getShortName() {
    return SHORT_NAME;
  }

  @Override
  @NotNull
  public String getDisplayName() {
    return DISPLAY_NAME;
  }

  @Override
  public boolean worksInBatchModeOnly() {
    return false;
  }
}