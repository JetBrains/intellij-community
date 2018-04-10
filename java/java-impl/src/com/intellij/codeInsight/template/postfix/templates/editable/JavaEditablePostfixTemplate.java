// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates.editable;

import com.intellij.codeInsight.template.impl.TemplateImpl;
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider;
import com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.util.Condition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.introduceVariable.IntroduceVariableBase;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public class JavaEditablePostfixTemplate extends EditablePostfixTemplate {
  private static final Condition<PsiElement> PSI_ERROR_FILTER = element -> !PsiTreeUtil.hasErrorElements(element);

  @NotNull private final Set<JavaPostfixTemplateExpressionCondition> myExpressionConditions;
  @NotNull private final LanguageLevel myMinimumLanguageLevel;
  private final boolean myUseTopmostExpression;

  public JavaEditablePostfixTemplate(@NotNull String templateName,
                                     @NotNull String templateText,
                                     @NotNull String example,
                                     @NotNull Set<JavaPostfixTemplateExpressionCondition> expressionConditions,
                                     @NotNull LanguageLevel minimumLanguageLevel,
                                     boolean useTopmostExpression,
                                     @NotNull PostfixTemplateProvider provider) {
    this(templateName, templateName, createTemplate(templateText), example, expressionConditions, minimumLanguageLevel, useTopmostExpression, provider);
  }

  public JavaEditablePostfixTemplate(@NotNull String templateId,
                                     @NotNull String templateName,
                                     @NotNull String templateText,
                                     @NotNull String example,
                                     @NotNull Set<JavaPostfixTemplateExpressionCondition> expressionConditions,
                                     @NotNull LanguageLevel minimumLanguageLevel,
                                     boolean useTopmostExpression,
                                     @NotNull PostfixTemplateProvider provider) {
    super(templateId, templateName, createTemplate(templateText), example, provider);
    myExpressionConditions = expressionConditions;
    myMinimumLanguageLevel = minimumLanguageLevel;
    myUseTopmostExpression = useTopmostExpression;
  }

  @NotNull
  private static TemplateImpl createTemplate(@NotNull String templateText) {
    TemplateImpl template = new TemplateImpl("fakeKey", templateText, "");
    template.setToReformat(true);
    template.parseSegments();
    return template;
  }

  public JavaEditablePostfixTemplate(@NotNull String templateId,
                                     @NotNull String templateName,
                                     @NotNull TemplateImpl liveTemplate,
                                     @NotNull String example,
                                     @NotNull Set<JavaPostfixTemplateExpressionCondition> expressionConditions,
                                     @NotNull LanguageLevel minimumLanguageLevel,
                                     boolean useTopmostExpression,
                                     @NotNull PostfixTemplateProvider provider) {
    super(templateId, templateName, liveTemplate, example, provider);
    myExpressionConditions = expressionConditions;
    myMinimumLanguageLevel = minimumLanguageLevel;
    myUseTopmostExpression = useTopmostExpression;
  }

  @NotNull
  public Set<JavaPostfixTemplateExpressionCondition> getExpressionConditions() {
    return myExpressionConditions;
  }

  @NotNull
  public LanguageLevel getMinimumLanguageLevel() {
    return myMinimumLanguageLevel;
  }

  public boolean isUseTopmostExpression() {
    return myUseTopmostExpression;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof JavaEditablePostfixTemplate)) return false;
    if (!super.equals(o)) return false;
    JavaEditablePostfixTemplate template = (JavaEditablePostfixTemplate)o;
    return myUseTopmostExpression == template.myUseTopmostExpression &&
           Objects.equals(myExpressionConditions, template.myExpressionConditions) &&
           myMinimumLanguageLevel == template.myMinimumLanguageLevel;
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), myExpressionConditions, myMinimumLanguageLevel);
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
      expressions = ContainerUtil.newArrayList(IntroduceVariableBase.collectExpressions(file, document, Math.max(offset - 1, 0), false));
    }

    return ContainerUtil.filter(expressions, e -> {
      if (!PSI_ERROR_FILTER.value(e) || !(e instanceof PsiExpression) || e.getTextRange().getEndOffset() != offset) {
        return false;
      }
      for (JavaPostfixTemplateExpressionCondition condition : myExpressionConditions) {
        if (condition.value((PsiExpression)e)) {
          return true;
        }
      }
      return myExpressionConditions.isEmpty();
    });
  }

  @NotNull
  @Override
  protected PsiElement getElementToRemove(@NotNull PsiElement element) {
    if (myUseTopmostExpression) {
      PsiElement parent = element.getParent();
      if (parent instanceof PsiExpressionStatement) {
        return parent;
      }
    }
    return element;
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
}
