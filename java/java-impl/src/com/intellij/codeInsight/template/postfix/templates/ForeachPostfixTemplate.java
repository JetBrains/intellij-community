/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.codeInsight.template.impl.VariableNode;
import com.intellij.codeInsight.template.macro.IterableComponentTypeMacro;
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.openapi.editor.Editor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.JavaCodeStyleSettingsFacade;

import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.IS_ITERABLE_OR_ARRAY;
import static com.intellij.codeInsight.template.postfix.util.JavaPostfixTemplatesUtils.JAVA_PSI_INFO;

public class ForeachPostfixTemplate extends StringBasedPostfixTemplate {
  public ForeachPostfixTemplate() {
    super("for", "for (T item : expr)", JAVA_PSI_INFO, IS_ITERABLE_OR_ARRAY);
  }

  @Override
  public void expandWithTemplateManager(TemplateManager manager, PsiElement expression, Editor editor) {

    String finalPart = JavaCodeStyleSettingsFacade.getInstance(expression.getProject()).isGenerateFinalLocals() ? "final " : "";
    Template template = manager.createTemplate("", "", "for (" + finalPart + "$type$ $name$ : $variable$) {\n    $END$\n}");
    MacroCallNode type = new MacroCallNode(new IterableComponentTypeMacro());
    MacroCallNode name = new MacroCallNode(new SuggestVariableNameMacro());
    String variable = "variable";
    type.addParameter(new VariableNode(variable, null));
    template.addVariable("type", type, type, false);
    template.addVariable("name", name, name, true);
    template.addVariable(variable, new TextExpression(expression.getText()), false);

    manager.startTemplate(editor, template);
  }
}