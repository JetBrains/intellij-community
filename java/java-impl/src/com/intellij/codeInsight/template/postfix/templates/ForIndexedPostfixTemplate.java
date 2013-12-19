package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.MacroCallNode;
import com.intellij.codeInsight.template.macro.SuggestVariableNameMacro;
import com.intellij.codeInsight.template.postfix.util.CommonUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiExpressionStatement;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public abstract class ForIndexedPostfixTemplate extends PostfixTemplate {
  protected ForIndexedPostfixTemplate(@NotNull String key, @NotNull String description, @NotNull String example) {
    super(key, description, example);
  }

  @Override
  public boolean isApplicable(@NotNull PsiElement context, @NotNull Document copyDocument, int newOffset) {
    PsiExpression expr = getTopmostExpression(context);
    if (expr == null || !(expr.getParent() instanceof PsiExpressionStatement)) return false;
    return CommonUtils.isNumber(expr.getType()) || CommonUtils.isArray(expr.getType()) || CommonUtils.isIterable(expr.getType());
  }

  @Override
  public void expand(@NotNull PsiElement context, @NotNull Editor editor) {
    PsiExpression expr = getTopmostExpression(context);
    if (expr == null) {
      CommonUtils.showErrorHint(context.getProject(), editor);
      return;
    }

    Pair<String, String> bounds = calculateBounds(expr);
    if (bounds == null) {
      CommonUtils.showErrorHint(context.getProject(), editor);
      return;
    }
    Project project = context.getProject();

    Document document = editor.getDocument();
    document.deleteString(expr.getTextRange().getStartOffset(), expr.getTextRange().getEndOffset());
    TemplateManager manager = TemplateManager.getInstance(project);

    Template template = manager.createTemplate("", "");
    template.setToReformat(true);
    template.addTextSegment("for (" + suggestIndexType(expr) + " ");
    MacroCallNode index = new MacroCallNode(new SuggestVariableNameMacro());
    String indexVariable = "index";
    template.addVariable(indexVariable, index, index, true);
    template.addTextSegment(" = " + bounds.first + "; ");
    template.addVariableSegment(indexVariable);
    template.addTextSegment(getComparativeSign(expr));
    template.addTextSegment(bounds.second);
    template.addTextSegment("; ");
    template.addVariableSegment(indexVariable);
    template.addTextSegment(getOperator());
    template.addTextSegment(") {\n");
    template.addEndVariable();
    template.addTextSegment("\n}");

    manager.startTemplate(editor, template);
  }

  @NotNull
  protected abstract String getComparativeSign(@NotNull PsiExpression expr);

  @Nullable
  protected abstract Pair<String, String> calculateBounds(@NotNull PsiExpression expression);

  @NotNull
  protected abstract String getOperator();

  @Nullable
  protected static String getExpressionBound(@NotNull PsiExpression expr) {
    PsiType type = expr.getType();
    if (CommonUtils.isNumber(type)) {
      return expr.getText();
    }
    else if (CommonUtils.isArray(type)) {
      return expr.getText() + ".length";
    }
    else if (CommonUtils.isIterable(type)) {
      return expr.getText() + ".size()";
    }
    return null;
  }

  @NotNull
  private static String suggestIndexType(@NotNull PsiExpression expr) {
    PsiType type = expr.getType();
    if (CommonUtils.isNumber(type)) {
      return type.getCanonicalText();
    }
    return "int";
  }
}