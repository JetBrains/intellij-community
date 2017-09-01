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
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.impl.PsiDiamondTypeUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.util.ObjectUtils.tryCast;

/**
 * @author Tagir Valeev
 */
class MigrateToStreamFix implements LocalQuickFix {
  private BaseStreamApiMigration myMigration;
  @Nullable private final String myCustomName;

  protected MigrateToStreamFix(BaseStreamApiMigration migration, @Nullable String customName) {
    myMigration = migration;
    myCustomName = customName;
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return myCustomName!= null? myCustomName: "Replace with "+myMigration.getReplacement();
  }

  @SuppressWarnings("DialogTitleCapitalization")
  @NotNull
  @Override
  public String getFamilyName() {
    return "Replace with Stream API equivalent";
  }

  @Override
  public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {
    PsiElement element = descriptor.getPsiElement();
    if (element instanceof PsiLoopStatement) {
      PsiLoopStatement loopStatement = (PsiLoopStatement)element;
      StreamSource source = StreamSource.tryCreate(loopStatement);
      PsiStatement body = loopStatement.getBody();
      if(body == null || source == null) return;
      TerminalBlock tb = TerminalBlock.from(source, body);
      migrate(project, body, tb);
    } else if(element instanceof PsiExpressionStatement) {
      PsiMethodCallExpression call = tryCast(((PsiExpressionStatement)element).getExpression(), PsiMethodCallExpression.class);
      if(call == null) return;

      PsiLambdaExpression lambda = SimplifyForEachInspection.extractLambdaFromForEach(call);
      if (lambda == null) return;
      PsiElement lambdaBody = lambda.getBody();
      SimplifyForEachInspection.ExistingStreamSource
        source = SimplifyForEachInspection.ExistingStreamSource.extractSource(call, lambda);
      if(source == null) return;
      TerminalBlock terminalBlock = SimplifyForEachInspection.extractTerminalBlock(lambdaBody, source);
      if (terminalBlock == null) return;

      migrate(project, lambdaBody, terminalBlock);
    }
  }

  private void migrate(@NotNull Project project, PsiElement block, TerminalBlock tb) {
    PsiElement result = myMigration.migrate(project, block, tb);
    if(result != null) {
      tb.operations().forEach(StreamApiMigrationInspection.Operation::cleanUp);
      simplifyAndFormat(project, result);
    }
  }

  static void simplifyAndFormat(@NotNull Project project, PsiElement result) {
    if (result == null) return;
    LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
    PsiDiamondTypeUtil.removeRedundantTypeArguments(result);
    result = SimplifyStreamApiCallChainsInspection.simplifyStreamExpressions(result);
    CodeStyleManager.getInstance(project).reformat(JavaCodeStyleManager.getInstance(project).shortenClassReferences(result));
  }
}
