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

import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.QuickFixFactory;
import com.intellij.codeInspection.*;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiImportList;
import com.intellij.psi.PsiImportStatementBase;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.FileTypeUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class MissortedImportsInspection extends GlobalSimpleInspectionTool {
  @NonNls
  public static final String SHORT_NAME = "MISSORTED_IMPORTS";

  @Override
  public void checkFile(@NotNull PsiFile file,
                        @NotNull InspectionManager manager,
                        @NotNull ProblemsHolder problemsHolder,
                        @NotNull GlobalInspectionContext globalContext,
                        @NotNull ProblemDescriptionsProcessor problemDescriptionsProcessor) {
    if (!(file instanceof PsiJavaFile javaFile) || FileTypeUtils.isInServerPageFile(file)) return;
    PsiImportList importList = javaFile.getImportList();
    PsiImportStatementBase[] imports = importList == null ? PsiImportStatementBase.EMPTY_ARRAY : importList.getAllImportStatements();
    int currentEntryIndex = 0;
    for (PsiImportStatementBase importStatement : imports) {
      ProgressManager.checkCanceled();
      // jsp include directive hack
      if (importStatement.isForeignFileImport()) {
        continue;
      }
      int entryIndex = JavaCodeStyleManager.getInstance(javaFile.getProject()).findEntryIndex(importStatement);
      if (entryIndex < currentEntryIndex) {
        // mis-sorted import found
        IntentionAction fix = QuickFixFactory.getInstance().createOptimizeImportsFix(true, javaFile);
        problemsHolder.registerProblem(importList, getDisplayNameText(), new IntentionWrapper(fix));
        return;
      }
      currentEntryIndex = entryIndex;
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

  @NotNull
  public static @Nls String getDisplayNameText() {
    return JavaAnalysisBundle.message("missorted.imports.inspection.display.name");
  }
}