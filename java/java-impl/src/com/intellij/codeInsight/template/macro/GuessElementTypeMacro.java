
// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.guess.GuessManager;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.template.*;
import com.intellij.codeInsight.template.impl.JavaTemplateUtil;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.GenericsUtil;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.LinkedHashSet;
import java.util.Set;

public final class GuessElementTypeMacro extends Macro {
  @Override
  public String getName() {
    return "guessElementType";
  }

  @Override
  public String getPresentableName() {
    return JavaBundle.message("macro.guess.element.type.of.container");
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "A";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, final ExpressionContext context) {
    PsiType[] types = guessTypes(params, context);
    if (types == null || types.length == 0) return null;
    return new PsiTypeResult(types[0], context.getProject());
  }

  @Override
  public LookupElement[] calculateLookupItems(Expression @NotNull [] params, ExpressionContext context) {
    PsiType[] types = guessTypes(params, context);
    if (types == null || types.length < 2) return null;
    Set<LookupElement> set = new LinkedHashSet<>();
    for (PsiType type : types) {
      JavaTemplateUtil.addTypeLookupItem(set, type);
    }
    return set.toArray(LookupElement.EMPTY_ARRAY);
  }

  private static PsiType @Nullable [] guessTypes(Expression[] params, ExpressionContext context) {
    if (params.length != 1) return null;
    final Result result = params[0].calculateResult(context);
    if (result == null) return null;

    Project project = context.getProject();

    PsiExpression expr = MacroUtil.resultToPsiExpression(result, context);
    if (expr == null) return null;
    PsiType[] types = GuessManager.getInstance(project).guessContainerElementType(expr, new TextRange(context.getTemplateStartOffset(), context.getTemplateEndOffset()));
    for (int i = 0; i < types.length; i++) {
      types[i] = GenericsUtil.getVariableTypeByExpressionType(types[i]);
    }
    return types;
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}