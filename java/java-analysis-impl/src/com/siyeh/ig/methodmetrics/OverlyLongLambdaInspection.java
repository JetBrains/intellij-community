// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.siyeh.ig.methodmetrics;

import com.intellij.psi.PsiCodeBlock;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiJavaToken;
import com.intellij.psi.PsiLambdaExpression;
import com.siyeh.InspectionGadgetsBundle;
import com.siyeh.ig.BaseInspectionVisitor;
import org.jetbrains.annotations.NotNull;

/**
 * @author Bas Leijdekkers
 */
public final class OverlyLongLambdaInspection extends MethodMetricInspection {

  private static final int DEFAULT_LIMIT = 3;

  @Override
  protected int getDefaultLimit() {
    return DEFAULT_LIMIT;
  }

  @Override
  protected String getConfigurationLabel() {
    return InspectionGadgetsBundle.message("non.comment.source.statements.limit.option");
  }

  @Override
  public @NotNull String buildErrorString(Object... infos) {
    final Integer statementCount = (Integer)infos[0];
    return InspectionGadgetsBundle.message("overly.long.lambda.problem.descriptor", statementCount);
  }

  @Override
  public BaseInspectionVisitor buildVisitor() {
    return new OverlyLongLambdaVisitor();
  }

  private class OverlyLongLambdaVisitor extends BaseInspectionVisitor {

    @Override
    public void visitLambdaExpression(@NotNull PsiLambdaExpression expression) {
      final PsiElement body = expression.getBody();
      if (!(body instanceof PsiCodeBlock block)) {
        return;
      }
      final PsiJavaToken brace = block.getLBrace();
      if (brace == null) {
        return;
      }
      final NCSSVisitor visitor = new NCSSVisitor();
      block.accept(visitor);
      final int count = visitor.getStatementCount();
      if (count <= getLimit()) {
        return;
      }
      registerErrorAtOffset(expression, 0, body.getStartOffsetInParent() + 1, Integer.valueOf(count));
    }
  }
}