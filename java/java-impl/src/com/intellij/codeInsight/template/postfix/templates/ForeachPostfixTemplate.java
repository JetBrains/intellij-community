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
import com.intellij.psi.PsiExpressionStatement;
import org.jetbrains.annotations.NotNull;

public class ForeachPostfixTemplate extends PostfixTemplate {
  public ForeachPostfixTemplate() {
    super("for", "Iterates over enumerable collection", "for (T item : collection)");
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression expr = getTopmostExpression(context);
    if (expr == null || !(expr.getParent() instanceof PsiExpressionStatement)) return false;
    return PostfixTemplatesUtils.isArray(expr.getType()) || PostfixTemplatesUtils.isIterable(expr.getType());
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expr = getTopmostExpression(context);
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