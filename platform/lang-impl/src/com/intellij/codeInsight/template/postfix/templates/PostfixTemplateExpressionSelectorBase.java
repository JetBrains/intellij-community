// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;


/**
 * Default abstract implementation of {@link PostfixTemplateExpressionSelector}.
 * You need impl method {@link #getNonFilteredExpressions}
 */
public abstract class PostfixTemplateExpressionSelectorBase implements PostfixTemplateExpressionSelector {

  protected final @NotNull Condition<? super PsiElement> myAdditionalCondition;

  public PostfixTemplateExpressionSelectorBase(@Nullable Condition<? super PsiElement> condition) {
    myAdditionalCondition = condition != null ? condition : Conditions.alwaysTrue();
  }

  private static final Condition<PsiElement> PSI_ERROR_FILTER = element -> !PsiTreeUtil.hasErrorElements(element);

  @Override
  public boolean hasExpression(@NotNull PsiElement context,
                               @NotNull Document copyDocument,
                               int newOffset) {
    return !getExpressions(context, copyDocument, newOffset).isEmpty();
  }

  protected Condition<PsiElement> getBorderOffsetFilter(final int offset) {
    return element -> element.getTextRange().getEndOffset() == offset;
  }

  @Override
  public @NotNull List<PsiElement> getExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
    return ContainerUtil.filter(getNonFilteredExpressions(context, document, offset), getFilters(offset));
  }

  @Override
  public @NotNull Function<PsiElement, String> getRenderer() {
    return element -> element.getText();
  }

  protected abstract List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int offset);

  protected Condition<PsiElement> getFilters(int offset) {
    return Conditions.and(getBorderOffsetFilter(offset), myAdditionalCondition);
  }

  protected Condition<PsiElement> getPsiErrorFilter() {
    return PSI_ERROR_FILTER;
  }
}
