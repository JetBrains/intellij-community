/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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

  @NotNull
  protected final Condition<PsiElement> myAdditionalCondition;

  public PostfixTemplateExpressionSelectorBase(@Nullable Condition<PsiElement> condition) {
    myAdditionalCondition = condition != null ? condition : Conditions.<PsiElement>alwaysTrue();
  }

  private static final Condition<PsiElement> PSI_ERROR_FILTER = new Condition<PsiElement>() {
    @Override
    public boolean value(PsiElement element) {
      return !PsiTreeUtil.hasErrorElements(element);
    }
  };

  @Override
  public boolean hasExpression(@NotNull PsiElement context,
                               @NotNull Document copyDocument,
                               int newOffset) {
    return !getExpressions(context, copyDocument, newOffset).isEmpty();
  }

  protected Condition<PsiElement> getBorderOffsetFilter(final int offset) {
    return new Condition<PsiElement>() {
      @Override
      public boolean value(PsiElement element) {
        return element.getTextRange().getEndOffset() == offset;
      }
    };
  }

  @NotNull
  @Override
  public List<PsiElement> getExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
    return ContainerUtil.filter(getNonFilteredExpressions(context, document, offset), getFilters(offset));
  }

  @NotNull
  public Function<PsiElement, String> getRenderer() {
    return new Function<PsiElement, String>() {
      @Override
      public String fun(@NotNull PsiElement element) {
        return element.getText();
      }
    };
  }

  protected abstract List<PsiElement> getNonFilteredExpressions(@NotNull PsiElement context, @NotNull Document document, int offset);

  protected Condition<PsiElement> getFilters(int offset) {
    return Conditions.and(getBorderOffsetFilter(offset), myAdditionalCondition);
  }

  protected Condition<PsiElement> getPsiErrorFilter() {
    return PSI_ERROR_FILTER;
  }
}
