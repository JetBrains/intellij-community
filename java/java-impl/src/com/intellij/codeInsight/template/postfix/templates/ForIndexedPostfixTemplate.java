// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbService;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.*;

public abstract class ForIndexedPostfixTemplate extends JavaEditablePostfixTemplate implements DumbAware {
  protected ForIndexedPostfixTemplate(@NotNull String templateName, @NotNull String templateText, @NotNull String example,
                                      @NotNull JavaPostfixTemplateProvider provider) {
    super(templateName, templateText, example,
          ContainerUtil.newHashSet(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayExpressionCondition(),
                                   new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateNumberExpressionCondition(),
                                   new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition(
                                     CommonClassNames.JAVA_UTIL_LIST)),
          LanguageLevel.JDK_1_3, true, provider);
  }


  @Override
  protected void addTemplateVariables(@NotNull PsiElement element, @NotNull Template template) {
    super.addTemplateVariables(element, template);
    MacroCallNode index = new MacroCallNode(new SuggestVariableNameMacro());
    template.addVariable("index", index, index, true);

    PsiExpression expr = (PsiExpression)element;
    DumbService.getInstance(element.getProject()).withAlternativeResolveEnabled(() -> {
      String bound = getExpressionBound(expr);
      if (bound != null) {
        template.addVariable("bound", new TextExpression(bound), false);
        template.addVariable("type", new TextExpression(suggestIndexType(expr)), false);
      }
    });
  }

  protected @Nullable String getExpressionBound(@NotNull PsiExpression expr) {
    PsiType type = expr.getType();
    if (isNumber(type)) {
      return expr.getText();
    }
    else if (isArray(type)) {
      return expr.getText() + ".length";
    }
    else if (isIterable(type)) {
      return expr.getText() + ".size()";
    }
    return null;
  }

  private static @NotNull String suggestIndexType(@NotNull PsiExpression expr) {
    PsiType type = expr.getType();
    if (Boolean.TRUE.equals(JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE) &&
         PsiUtil.isAvailable(JavaFeature.LVTI, expr)) {
      return JavaKeywords.VAR;
    }
    if (isNumber(type)) {
      return type.getCanonicalText();
    }
    return JavaKeywords.INT;
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }
}