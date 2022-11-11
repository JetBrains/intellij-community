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

import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.StreamSource;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

class MigrateToStreamFix implements LocalQuickFix {
  private final BaseStreamApiMigration myMigration;

  protected MigrateToStreamFix(BaseStreamApiMigration migration) {
    myMigration = migration;
  }

  @Nls
  @NotNull
  @Override
  public String getName() {
    return JavaAnalysisBundle.message("replace.with.0", myMigration.getReplacement());
  }

  @NotNull
  @Override
  public String getFamilyName() {
    return JavaBundle.message("quickfix.family.replace.with.stream.api.equivalent");
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    // Has non-trivial fields but safe
    return this;
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
    simplify(project, result);
  }

  static void simplify(@NotNull Project project, PsiElement result) {
    if (result == null) return;
    LambdaCanBeMethodReferenceInspection.replaceAllLambdasWithMethodReferences(result);
    RemoveRedundantTypeArgumentsUtil.removeRedundantTypeArguments(result);
    result = SimplifyStreamApiCallChainsInspection.simplifyStreamExpressions(result, true);
    removeRedundantPatternVariables(result);
    JavaCodeStyleManager.getInstance(project).shortenClassReferences(result);
  }

  private static void removeRedundantPatternVariables(PsiElement element) {
    for (PsiLambdaExpression lambda : PsiTreeUtil.collectElementsOfType(element, PsiLambdaExpression.class)) {
      PsiElement body = lambda.getBody();
      if (body instanceof PsiExpression) {
        PsiExpression expression = (PsiExpression)body;
        if (PsiType.BOOLEAN.equals(expression.getType())) {
          List<PsiPatternVariable> variables = JavaPsiPatternUtil.getExposedPatternVariablesIgnoreParent(expression);
          for (PsiPatternVariable variable : variables) {
            if (variable.getPattern() instanceof PsiTypeTestPattern pattern && pattern.getParent() instanceof PsiDeconstructionList) {
              continue;
            }
            if (!VariableAccessUtils.variableIsUsed(variable, expression)) {
              variable.delete();
            }
          }
        }
      }
    }
  }
}
