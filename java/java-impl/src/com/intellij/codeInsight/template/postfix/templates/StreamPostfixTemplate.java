// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorAllExpressionsWithCurrentOffset;

public class StreamPostfixTemplate extends StringBasedPostfixTemplate implements DumbAware {
  private static final Condition<PsiElement> IS_SUPPORTED_ARRAY = element -> {
    if (!(element instanceof PsiExpression)) return false;

    if (element instanceof PsiAssignmentExpression && element.getParent() instanceof PsiExpressionStatement) return false;

    PsiType type = ((PsiExpression)element).getType();
    if (!(type instanceof PsiArrayType)) return false;

    PsiType componentType = ((PsiArrayType)type).getComponentType();
    if (!(componentType instanceof PsiPrimitiveType)) return true;

    return componentType.equals(PsiTypes.intType()) || componentType.equals(PsiTypes.longType()) || componentType.equals(PsiTypes.doubleType());
  };

  public StreamPostfixTemplate() {
    super("stream", "Arrays.stream(expr)", JavaPostfixTemplatesUtils.atLeastJava8Selector(selectorAllExpressionsWithCurrentOffset(IS_SUPPORTED_ARRAY)));
  }

  @Override
  public @Nullable String getTemplateString(@NotNull PsiElement element) {
    return "java.util.Arrays.stream($expr$)";
  }

  @Override
  protected PsiElement getElementToRemove(PsiElement expr) {
    return expr;
  }
}
