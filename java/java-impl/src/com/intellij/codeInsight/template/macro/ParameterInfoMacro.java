// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.macro;

import com.intellij.codeInsight.template.*;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ParameterInfoMacro extends Macro {

  @Override
  public String getName() {
    return "parameterInfo";
  }

  @Override
  public @NotNull String getDefaultValue() {
    return "a";
  }

  @Override
  public Result calculateResult(Expression @NotNull [] params, final ExpressionContext context) {
    PsiElement place = context.getPsiElementAtStartOffset();
    while(place != null){
      if (place instanceof PsiParameterListOwner method){
        PsiParameter[] parameters = method.getParameterList().getParameters();
        if (parameters.length == 0) {
          return new TextResult("");
        }
        String parameterInfo = Stream.of(parameters)
          .map(p -> p.getName() + " = \"+" + getPresentation(p))
          .collect(Collectors.joining("+\", ", "\"", ""));
        return new TextResult(parameterInfo);
      }
      place = place.getParent();
    }
    return new TextResult("");
  }

  private static @NotNull @NlsSafe String getPresentation(PsiParameter p) {
    PsiType type = p.getType();
    return switch (type.getArrayDimensions()) {
      case 0 -> p.getName();
      case 1 -> CommonClassNames.JAVA_UTIL_ARRAYS + ".toString(" + p.getName() + ")";
      default -> CommonClassNames.JAVA_UTIL_ARRAYS + ".deepToString(" + p.getName() + ")";
    };
  }

  @Override
  public boolean isAcceptableInContext(TemplateContextType context) {
    return context instanceof JavaCodeContextType;
  }

}
