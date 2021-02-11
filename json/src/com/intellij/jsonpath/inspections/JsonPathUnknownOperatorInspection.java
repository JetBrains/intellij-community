// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.JsonBundle;
import com.intellij.jsonpath.JsonPathConstants;
import com.intellij.jsonpath.psi.JsonPathBinaryConditionalOperator;
import com.intellij.jsonpath.psi.JsonPathTypes;
import com.intellij.jsonpath.psi.JsonPathVisitor;
import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.jsonpath.ui.JsonPathEvaluateManager.JSON_PATH_EVALUATE_EXPRESSION_KEY;

final class JsonPathUnknownOperatorInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JsonPathVisitor() {
      @Override
      public void visitBinaryConditionalOperator(@NotNull JsonPathBinaryConditionalOperator operator) {
        super.visitBinaryConditionalOperator(operator);

        ASTNode namedOp = operator.getNode().findChildByType(JsonPathTypes.NAMED_OP);
        if (namedOp == null) return;

        String operatorName = namedOp.getText();

        if (!JsonPathConstants.STANDARD_NAMED_OPERATORS.contains(operatorName)) {
          boolean isEvaluateExpr = Boolean.TRUE.equals(holder.getFile().getUserData(JSON_PATH_EVALUATE_EXPRESSION_KEY));
          if (isEvaluateExpr) {
            holder.registerProblem(operator, JsonBundle.message("inspection.message.jsonpath.unsupported.jayway.operator", operatorName),
                                   ProblemHighlightType.ERROR);
          }
          else {
            holder.registerProblem(operator, null, JsonBundle.message("inspection.message.jsonpath.unknown.operator.name", operatorName));
          }
        }
      }
    };
  }
}
