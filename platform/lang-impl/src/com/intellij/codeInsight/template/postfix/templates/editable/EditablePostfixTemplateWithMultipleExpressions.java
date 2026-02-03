// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

/**
 * Base class for editable templates with applicable expressions conditions.
 * Template data is backed by live template.
 * It supports selecting the expression a template is applied to.
 *
 * @param <ConditionType> expression condition type
 * @see <a href="https://plugins.jetbrains.com/docs/intellij/advanced-postfix-templates.html">Advanced Postfix Templates (IntelliJ Platform Docs)</a>
 */
public abstract class EditablePostfixTemplateWithMultipleExpressions<ConditionType extends PostfixTemplateExpressionCondition>
  extends EditablePostfixTemplate {

  protected final @NotNull Set<? extends ConditionType> myExpressionConditions;
  protected final boolean myUseTopmostExpression;

  protected EditablePostfixTemplateWithMultipleExpressions(@NotNull String templateId,
                                                           @NotNull String templateName,
                                                           @NotNull TemplateImpl liveTemplate,
                                                           @NotNull String example,
                                                           @NotNull Set<? extends ConditionType> expressionConditions,
                                                           boolean useTopmostExpression,
                                                           @NotNull PostfixTemplateProvider provider) {
    super(templateId, templateName, liveTemplate, example, provider);
    myExpressionConditions = expressionConditions;
    myUseTopmostExpression = useTopmostExpression;
  }

  protected EditablePostfixTemplateWithMultipleExpressions(@NotNull String templateId,
                                                           @NotNull String templateName,
                                                           @NotNull String templateKey,
                                                           @NotNull TemplateImpl liveTemplate,
                                                           @NotNull String example,
                                                           @NotNull Set<? extends ConditionType> expressionConditions,
                                                           boolean useTopmostExpression,
                                                           @NotNull PostfixTemplateProvider provider) {
    super(templateId, templateName, templateKey, liveTemplate, example, provider);
    myExpressionConditions = expressionConditions;
    myUseTopmostExpression = useTopmostExpression;
  }

  protected static @NotNull TemplateImpl createTemplate(@NotNull String templateText) {
    TemplateImpl template = new TemplateImpl("fakeKey", templateText, "");
    template.setToReformat(true);
    template.parseSegments();
    return template;
  }


  @Override
  protected @NotNull PsiElement getElementToRemove(@NotNull PsiElement element) {
    if (myUseTopmostExpression) {
      return getTopmostExpression(element);
    }
    return element;
  }

  protected abstract @NotNull PsiElement getTopmostExpression(@NotNull PsiElement element);

  public @NotNull Set<? extends ConditionType> getExpressionConditions() {
    return Collections.unmodifiableSet(myExpressionConditions);
  }

  public boolean isUseTopmostExpression() {
    return myUseTopmostExpression;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    EditablePostfixTemplateWithMultipleExpressions<?> that = (EditablePostfixTemplateWithMultipleExpressions<?>)o;
    return myUseTopmostExpression == that.myUseTopmostExpression &&
           Objects.equals(myExpressionConditions, that.myExpressionConditions);
  }

  protected @NotNull Condition<PsiElement> getExpressionCompositeCondition() {
    return e -> {
      for (ConditionType condition : myExpressionConditions) {
        //noinspection unchecked
        if (condition.value(e)) {
          return true;
        }
      }
      return myExpressionConditions.isEmpty();
    };
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myExpressionConditions, myUseTopmostExpression);
  }
}
