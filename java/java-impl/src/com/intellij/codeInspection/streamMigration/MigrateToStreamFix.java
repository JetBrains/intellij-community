/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.SimplifyStreamApiCallChainsInspection;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.StreamSource;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiLoopStatement;
import com.intellij.psi.PsiStatement;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Tagir Valeev
 */
class MigrateToStreamFix implements LocalQuickFix {
  private BaseStreamApiMigration myMigration;

  protected MigrateToStreamFix(BaseStreamApiMigration migration) {
    myMigration = migration;
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return "Replace with "+myMigration.getReplacement();
  }

  @SuppressWarnings("DialogTitleCapitalization")
  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with Stream API equivalent";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiLoopStatement loopStatement = tryCast(descriptor.getPsiElement(), PsiLoopStatement.class);
    if (loopStatement == null) return;
    StreamSource source = StreamSource.tryCreate(loopStatement);
    PsiStatement body = loopStatement.getBody();
    if(body == null || source == null) return;
    TerminalBlock tb = TerminalBlock.from(source, body);
    PsiElement result = myMigration.migrate(project, body, tb);
    if (result == null) return;
    tb.operations().forEach(StreamApiMigrationInspection.Operation::cleanUp);
    simplifyAndFormat(project, result);
  }

  static void simplifyAndFormat(@NotNull Project project, PsiElement result) {
    if (result == null) return;
    LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
    PsiDiamondTypeUtil.removeRedundantTypeArguments(result);
    result = SimplifyStreamApiCallChainsInspection.simplifyStreamExpressions(result);
    CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
  }
}
