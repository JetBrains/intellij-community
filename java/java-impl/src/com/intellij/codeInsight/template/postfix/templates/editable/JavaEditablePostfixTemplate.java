// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Conditions;
import com.intellij.openapi.util.TextRange;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.CommonJavaRefactoringUtil;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class JavaEditablePostfixTemplate
  extends EditablePostfixTemplateWithMultipleExpressions<JavaPostfixTemplateExpressionCondition> {
  private static final Condition<PsiElement> PSI_ERROR_FILTER = element -> !PsiTreeUtil.hasErrorElements(element);

  @NotNull private final LanguageLevel myMinimumLanguageLevel;

  public JavaEditablePostfixTemplate(@NotNull String templateName,
                                     @NotNull String templateText,
                                     @NotNull String example,
                                     @NotNull Set<? extends JavaPostfixTemplateExpressionCondition> expressionConditions,
                                     @NotNull LanguageLevel minimumLanguageLevel,
                                     boolean useTopmostExpression,
                                     @NotNull PostfixTemplateProvider provider) {
    this(templateName, templateName, createTemplate(templateText), example, expressionConditions, minimumLanguageLevel,
         useTopmostExpression, provider);
  }

  public JavaEditablePostfixTemplate(@NotNull String templateId,
                                     @NotNull String templateName,
                                     @NotNull String templateText,
                                     @NotNull String example,
                                     @NotNull Set<? extends JavaPostfixTemplateExpressionCondition> expressionConditions,
                                     @NotNull LanguageLevel minimumLanguageLevel,
                                     boolean useTopmostExpression,
                                     @NotNull PostfixTemplateProvider provider) {
    super(templateId, templateName, createTemplate(templateText), example, expressionConditions, useTopmostExpression, provider);
    myMinimumLanguageLevel = minimumLanguageLevel;
  }

  public JavaEditablePostfixTemplate(@NotNull String templateId,
                                     @NotNull String templateName,
                                     @NotNull TemplateImpl liveTemplate,
                                     @NotNull String example,
                                     @NotNull Set<? extends JavaPostfixTemplateExpressionCondition> expressionConditions,
                                     @NotNull LanguageLevel minimumLanguageLevel,
                                     boolean useTopmostExpression,
                                     @NotNull PostfixTemplateProvider provider) {
    super(templateId, templateName, liveTemplate, example, expressionConditions, useTopmostExpression, provider);
    myMinimumLanguageLevel = minimumLanguageLevel;
  }


  @NotNull
  public LanguageLevel getMinimumLanguageLevel() {
    return myMinimumLanguageLevel;
  }

  @Override
  protected List<PsiElement> getExpressions(@NotNull PsiElement context, @NotNull Document document, int offset) {
    if (DumbService.getInstance(context.getProject()).isDumb()) return Collections.emptyList();
    if (!PsiUtil.getLanguageLevel(context).isAtLeast(myMinimumLanguageLevel)) {
      return Collections.emptyList();
    }
    List<PsiElement> expressions;
    if (myUseTopmostExpression) {
      expressions = ContainerUtil.createMaybeSingletonList(JavaPostfixTemplatesUtils.getTopmostExpression(context));
    }
    else {
      PsiFile file = context.getContainingFile();
      expressions = new ArrayList<>(CommonJavaRefactoringUtil.collectExpressions(file, document, Math.max(offset - 1, 0), false));
    }


    return ContainerUtil.filter(expressions, Conditions.and(e -> PSI_ERROR_FILTER.value(e)
                                                                 && e instanceof PsiExpression && e.getTextRange().getEndOffset() == offset, getExpressionCompositeCondition()));
  }

  @NotNull
  @Override
  protected PsiElement getTopmostExpression(@NotNull PsiElement element) {
    PsiElement parent = element.getParent();
    if (parent instanceof PsiExpressionStatement) {
      return parent;
    }

    return element;
  }

  @Override
  protected @NotNull TextRange getRangeToRemove(@NotNull PsiElement element) {
    PsiElement toRemove = getElementToRemove(element);
    if (toRemove instanceof PsiExpressionStatement) {
      PsiElement lastChild = toRemove.getLastChild();
      while (lastChild instanceof PsiComment || lastChild instanceof PsiWhiteSpace) {
        lastChild = lastChild.getPrevSibling();
      }
      if (lastChild != null) {
        return TextRange.create(toRemove.getTextRange().getStartOffset(), lastChild.getTextRange().getEndOffset());
      }
    }
    return toRemove.getTextRange();
  }

  @NotNull
  @Override
  protected Function<PsiElement, String> getElementRenderer() {
    return JavaPostfixTemplatesUtils.getRenderer();
  }

  @Override
  public boolean isBuiltin() {
    return false;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    JavaEditablePostfixTemplate template = (JavaEditablePostfixTemplate)o;
    return myMinimumLanguageLevel == template.myMinimumLanguageLevel;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myMinimumLanguageLevel);
  }
}
