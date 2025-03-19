// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.generation.surroundWith.JavaWithIfExpressionSurrounder;
import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.project.DumbAware;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.util.CommonJavaRefactoringUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.*;

public class IfStatementPostfixTemplate extends IfPostfixTemplateBase implements DumbAware {
  public IfStatementPostfixTemplate() {
    super(JAVA_PSI_INFO, selectorTopmost(IS_BOOLEAN));
  }

  @Override
  protected PsiElement getWrappedExpression(PsiElement expression) {
    return CommonJavaRefactoringUtil.unparenthesizeExpression((PsiExpression)expression);
  }

  @Override
  protected @NotNull Surrounder getSurrounder() {
    return new JavaWithIfExpressionSurrounder();
  }
}

