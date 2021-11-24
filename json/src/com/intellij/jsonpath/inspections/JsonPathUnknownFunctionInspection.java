// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.JsonBundle;
import com.intellij.jsonpath.JsonPathConstants;
import com.intellij.jsonpath.psi.JsonPathFunctionCall;
import com.intellij.jsonpath.psi.JsonPathId;
import com.intellij.jsonpath.psi.JsonPathVisitor;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

import static com.intellij.jsonpath.ui.JsonPathEvaluateManager.JSON_PATH_EVALUATE_EXPRESSION_KEY;

public final class JsonPathUnknownFunctionInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JsonPathVisitor() {
      @Override
      public void visitFunctionCall(@NotNull JsonPathFunctionCall call) {
        super.visitFunctionCall(call);

        JsonPathId functionId = call.getId();
        String functionName = functionId.getText();

        if (!JsonPathConstants.STANDARD_FUNCTIONS.containsKey(functionName)) {
          boolean isEvaluateExpr = Boolean.TRUE.equals(holder.getFile().getUserData(JSON_PATH_EVALUATE_EXPRESSION_KEY));
          if (isEvaluateExpr) {
            holder.registerProblem(functionId, JsonBundle.message("inspection.message.jsonpath.unsupported.jayway.function", functionName),
                                   ProblemHighlightType.ERROR);
          }
          else {
            holder.registerProblem(functionId, null, JsonBundle.message("inspection.message.jsonpath.unknown.function.name", functionName));
            // todo Suppress for name quick fix
          }
        }
      }
    };
  }
}
