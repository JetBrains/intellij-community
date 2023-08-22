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
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.psi.*;
import com.intellij.psi.util.FileTypeUtils;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.fixes.DeleteImportFix;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class UnusedImportInspection extends GlobalSimpleInspectionTool {
  @NonNls
  public static final String SHORT_NAME = "UNUSED_IMPORT";

  @Override
  public void checkFile(@NotNull PsiFile file,
                        @NotNull InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull GlobalInspectionContext globalContext,
                        @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    if (!(file instanceof PsiJavaFile javaFile) || FileTypeUtils.isInServerPageFile(file)) return;
    final ImportsAreUsedVisitor visitor = new ImportsAreUsedVisitor(javaFile);
    javaFile.accept(visitor);
    for (PsiImportStatementBase unusedImportStatement : visitor.getUnusedImportStatements()) {
      PsiJavaCodeReferenceElement reference = unusedImportStatement.getImportReference();
      if (reference != null &&
          reference.multiResolve(false).length > 0 &&
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
  public boolean worksInBatchModeOnly() {
    return false;
  }

  public static @Nls String getDisplayNameText() {
    return JavaAnalysisBundle.message("unused.import.display.name");
  }
}