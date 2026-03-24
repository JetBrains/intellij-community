// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.generation.surroundWith.JavaWithIfExpressionSurrounder;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_NOT_PRIMITIVE;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.JAVA_PSI_INFO;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.selectorTopmost;

public class IsNullCheckPostfixTemplate extends SurroundPostfixTemplateBase implements DumbAware {
  public IsNullCheckPostfixTemplate() {
    super("null", "if (expr == null)", JAVA_PSI_INFO, selectorTopmost(IS_NOT_PRIMITIVE));
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    return super.isApplicable(context, copyDocument, newOffset) && !JavaPostfixTemplatesUtils.isInExpressionFile(context);
  }

  @Override
  protected @NotNull String getTail() {
    return "== null";
  }

  @Override
  protected @NotNull Surrounder getSurrounder() {
    return new JavaWithIfExpressionSurrounder();
  }
}