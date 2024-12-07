// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.daemon.impl.analysis.JavaGenericsUtil;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiTypesUtil;
import org.jetbrains.annotations.NotNull;

public final class IterableComponentTypeMacro extends Macro {
  @Override
  public String getName() {
    return "iterableComponentType";
  }

  @Override
  public String getPresentableName() {
    return JavaBundle.message("macro.iterable.component.type");
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "a";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    Project project = context.getProject();

    PsiExpression expr = MacroUtil.resultToPsiExpression(result, context);
    if (expr == null) return null;

    PsiType component = JavaGenericsUtil.getCollectionItemType(expr);
    if (component != null) {
      PsiType finalType = NullabilityUtil.removeTopLevelNullabilityAnnotations(
        project, PsiTypesUtil.removeExternalAnnotations(GenericsUtil.getVariableTypeByExpressionType(component)));
      return new PsiTypeResult(finalType, project);
    }

    return null;
  }

  @Override
  public Result calculateQuickResult(Expression @NotNull [] params, ExpressionContext context) {
    return calculateResult(params, context);
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}
