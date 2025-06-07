// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.impl.VariableNode;
import com.intellij.codeInsight.template.macro.IterableComponentTypeMacro;
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.java.syntax.parser.JavaKeywords;
import com.intellij.openapi.project.DumbAware;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.codeStyle.JavaFileCodeStyleFacade;
import com.intellij.psi.util.PsiUtil;
import com.intellij.refactoring.JavaRefactoringSettings;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class ForeachPostfixTemplate extends JavaEditablePostfixTemplate implements DumbAware {
  public ForeachPostfixTemplate(@NotNull String templateName, @NotNull JavaPostfixTemplateProvider provider) {
    super(templateName, "for ($FINAL$$TYPE$ $NAME$ : $EXPR$) {\n    $END$\n}", "for (T item : expr)",
          ContainerUtil.newHashSet(new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateArrayExpressionCondition(),
                                   new JavaPostfixTemplateExpressionCondition.JavaPostfixTemplateExpressionFqnCondition(
                                     CommonClassNames.JAVA_LANG_ITERABLE)),
          LanguageLevel.JDK_1_5, true, provider);
  }

  @Override
  public boolean isBuiltin() {
    return true;
  }

  @Override
  protected void addTemplateVariables(@NotNull PsiElement element, @NotNull Template template) {
    MacroCallNode type = new MacroCallNode(new IterableComponentTypeMacro());
    type.addParameter(new VariableNode("EXPR", null));

    if (Boolean.TRUE.equals(JavaRefactoringSettings.getInstance().INTRODUCE_LOCAL_CREATE_VAR_TYPE) &&
        element instanceof PsiExpression expr &&
        PsiUtil.isAvailable(JavaFeature.LVTI, expr)) {
      template.addVariable("TYPE", new TextExpression(JavaKeywords.VAR), false);
    }
    else {
      template.addVariable("TYPE", type, type, false);
    }

    MacroCallNode name = new MacroCallNode(new SuggestVariableNameMacro());
    template.addVariable("NAME", name, name, true);

    String finalPart = JavaFileCodeStyleFacade.forContext(element.getContainingFile()).isGenerateFinalLocals() ? "final " : null;
    if (finalPart != null) {
      template.addVariable("FINAL", new TextExpression(finalPart), false);
    }
  }
}