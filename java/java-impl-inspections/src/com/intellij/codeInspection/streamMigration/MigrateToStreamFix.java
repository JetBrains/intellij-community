// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.streamMigration;

import com.intellij.codeInspection.LambdaCanBeMethodReferenceInspection;
import com.intellij.codeInspection.RemoveRedundantTypeArgumentsUtil;
import com.intellij.codeInspection.SimplifyStreamApiCallChainsInspection;
import com.intellij.codeInspection.streamMigration.StreamApiMigrationInspection.StreamSource;
import com.intellij.java.JavaBundle;
import com.intellij.java.analysis.JavaAnalysisBundle;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.PsiUpdateModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.util.JavaPsiPatternUtil;
import com.intellij.psi.util.PsiTreeUtil;
import com.siyeh.ig.psiutils.VariableAccessUtils;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.List;

import static com.intellij.util.ObjectUtils.tryCast;

class MigrateToStreamFix extends PsiUpdateModCommandQuickFix {
  private final BaseStreamApiMigration myMigration;

  protected MigrateToStreamFix(BaseStreamApiMigration migration) {
    myMigration = migration;
  }

  @Override
  public @Nls @NotNull String getName() {
    return JavaAnalysisBundle.message("replace.with.stream.api.fix", myMigration.getReplacement());
  }

  @Override
  public @NotNull String getFamilyName() {
    return JavaBundle.message("quickfix.family.replace.with.stream.api.equivalent");
  }

  @Override
  protected void applyFix(@NotNull Project project, @NotNull PsiElement element, @NotNull ModPsiUpdater updater) {
    PsiLoopStatement loopStatement = tryCast(element, PsiLoopStatement.class);
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
      if (body instanceof PsiExpression expression && PsiTypes.booleanType().equals(expression.getType())) {
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
