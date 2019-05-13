// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.Nullability;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.codeInspection.dataFlow.NullabilityUtil;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;

public class OptionalPostfixTemplate extends JavaEditablePostfixTemplate {
  public OptionalPostfixTemplate(@NotNull JavaPostfixTemplateProvider provider) {
    super("opt",
          "java.util.$OPTIONAL_CLASS$.$OPTIONAL_METHOD$($EXPR$)",
          "Optional.ofNullable(expr)",
          Collections.singleton(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNonVoidExpressionCondition()),
          LanguageLevel.JDK_1_8, true, provider);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }

  @Override
  protected void addTemplateVariables(@NotNull PsiElement element, @NotNull Template template) {
    super.addTemplateVariables(element, template);
    template.addVariable("OPTIONAL_CLASS", new TextExpression(getClassName(element)), false);
    template.addVariable("OPTIONAL_METHOD", new TextExpression(getMethodName(element)), false);
  }

  private static String getMethodName(@NotNull PsiElement element) {
    if (element instanceof PsiExpression && Nullability.NOT_NULL == NullabilityUtil.getExpressionNullability((PsiExpression)element, true)) {
      return "of";
    }
    return "ofNullable";
  }

  @NotNull
  private static String getClassName(@NotNull PsiElement element) {
    String className = "Optional";

    PsiType type = element instanceof PsiExpression ? ((PsiExpression)element).getType() : null;
    if (type instanceof PsiPrimitiveType) {
      if (PsiType.INT.equals(type)) {
        className = "OptionalInt";
      }
      else if (PsiType.DOUBLE.equals(type)) {
        className = "OptionalDouble";
      }
      else if (PsiType.LONG.equals(type)) {
        className = "OptionalLong";
      }
    }
    return className;
  }
}
