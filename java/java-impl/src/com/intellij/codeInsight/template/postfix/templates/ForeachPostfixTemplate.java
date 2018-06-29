// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.impl.VariableNode;
import com.intellij.codeInsight.template.macro.IterableComponentTypeMacro;
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaEditablePostfixTemplate;
import com.intellij.codeInsight.template.postfix.templates.editable.JavaPostfixTemplateExpressionCondition;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

public class ForeachPostfixTemplate extends JavaEditablePostfixTemplate {
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
    template.addVariable("TYPE", type, type, false);

    MacroCallNode name = new MacroCallNode(new SuggestVariableNameMacro());
    template.addVariable("NAME", name, name, true);

    String finalPart = JavaCodeStyleSettingsFacade.getInstance(element.getProject()).isGenerateFinalLocals() ? "final " : null;
    if (finalPart != null) {
      template.addVariable("FINAL", new TextExpression(finalPart), false);
    }
  }
}