// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.lang.surroundWith.Surrounder;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base for surrounding postfix templates that utilize existing {@link Surrounder} implementations.
 *
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/advanced-postfix-templates.html">Advanced Postfix Templates (IntelliJ Platform Docs)</a>
 */
public abstract class SurroundPostfixTemplateBase extends PostfixTemplateWithExpressionSelector {

  protected final @NotNull PostfixTemplatePsiInfo myPsiInfo;

  /**
   * @deprecated use {@link #SurroundPostfixTemplateBase(String, String, PostfixTemplatePsiInfo, PostfixTemplateExpressionSelector, PostfixTemplateProvider)}
   */
  @Deprecated(forRemoval = true)
  protected SurroundPostfixTemplateBase(@NotNull @NlsSafe String name,
                                        @NotNull @NlsSafe String descr,
                                        @NotNull PostfixTemplatePsiInfo psiInfo,
                                        @NotNull PostfixTemplateExpressionSelector selector) {
    this(name, descr, psiInfo, selector, null);
  }

  protected SurroundPostfixTemplateBase(@NotNull @NlsSafe String name,
                                        @NotNull @NlsSafe String descr,
                                        @NotNull PostfixTemplatePsiInfo psiInfo,
                                        @NotNull PostfixTemplateExpressionSelector selector,
                                        @Nullable PostfixTemplateProvider provider) {
    super(null, name, descr, selector, provider);
    myPsiInfo = psiInfo;
  }

  @Override
  public final void expandForChooseExpression(@NotNull PsiElement expression, final @NotNull Editor editor) {
    PsiElement replace = getReplacedExpression(expression);
    TextRange range = PostfixTemplatesUtils.surround(getSurrounder(), editor, replace);

    if (range != null) {
      editor.getCaretModel().moveToOffset(range.getStartOffset());
    }
  }

  protected PsiElement getReplacedExpression(@NotNull PsiElement expression) {
    PsiElement wrappedExpression = getWrappedExpression(expression);
    return expression.replace(wrappedExpression);
  }

  protected PsiElement getWrappedExpression(PsiElement expression) {
    if (StringUtil.isEmpty(getHead()) && StringUtil.isEmpty(getTail())) {
      return expression;
    }
    return createNew(expression);
  }

  protected PsiElement createNew(PsiElement expression) {
    return myPsiInfo.createExpression(expression, getHead(), getTail());
  }

  protected @NotNull @NlsSafe String getHead() {
    return "";
  }

  protected @NotNull @NlsSafe String getTail() {
    return "";
  }

  protected abstract @NotNull Surrounder getSurrounder();
}

