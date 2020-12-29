// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.jsonpath.inspections;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.json.JsonBundle;
import com.intellij.jsonpath.JsonPathConstants;
import com.intellij.jsonpath.psi.JsonPathFunctionName;
import com.intellij.jsonpath.psi.JsonPathVisitor;
import com.intellij.psi.PsiElementVisitor;
import org.jetbrains.annotations.NotNull;

final class JsonPathUnknownFunctionInspection extends LocalInspectionTool {
  @Override
  public @NotNull PsiElementVisitor buildVisitor(@NotNull ProblemsHolder holder, boolean isOnTheFly) {
    return new JsonPathVisitor() {
      @Override
      public void visitFunctionName(@NotNull JsonPathFunctionName o) {
        super.visitFunctionName(o);

        if (!JsonPathConstants.STANDARD_FUNCTIONS.containsKey(o.getText())) {
          holder.registerProblem(o, null, JsonBundle.message("inspection.message.jsonpath.unknown.function.name", o.getText()));

          // todo Suppress for name quick fix
        }
      }
    };
  }
}
