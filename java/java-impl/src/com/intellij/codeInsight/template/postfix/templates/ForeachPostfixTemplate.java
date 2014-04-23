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
import com.intellij.codeInsight.template.postfix.util.PostfixTemplatesUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.NotNull;

public class ForeachPostfixTemplate extends PostfixTemplate {
  public ForeachPostfixTemplate() {
    super("for", "Iterates over enumerable collection", "for (T item : collection)");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression expr = PostfixTemplatesUtils.getTopmostExpression(context);
    return expr != null && (PostfixTemplatesUtils.isArray(expr.getType()) || PostfixTemplatesUtils.isIterable(expr.getType()));
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expr = PostfixTemplatesUtils.getTopmostExpression(context);
    if (expr == null) return;
    Project project = context.getProject();

    Document document = editor.getDocument();
    document.deleteString(expr.getTextRange().getStartOffset(), expr.getTextRange().getEndOffset());
    TemplateManager manager = TemplateManager.getInstance(project);

    Template template = manager.createTemplate("", "");
    template.setToReformat(true);
    template.addTextSegment("for (");
    MacroCallNode type = new MacroCallNode(new IterableComponentTypeMacro());

    String variable = "variable";
    type.addParameter(new VariableNode(variable, null));
    MacroCallNode name = new MacroCallNode(new SuggestVariableNameMacro());

    template.addVariable("type", type, type, false);
    template.addTextSegment(" ");
    template.addVariable("name", name, name, true);

    template.addTextSegment(" : ");
    template.addVariable(variable, new TextExpression(expr.getText()), false);
    template.addTextSegment(") {\n");
    template.addEndVariable();
    template.addTextSegment("\n}");

    manager.startTemplate(editor, template);
  }
}