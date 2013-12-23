package com.intellij.codeInsight.template.postfix.templates;

import com.intellij.codeInsight.CodeInsightUtilCore;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * @author ignatov
 */
public abstract class StatementPostfixTemplateBase extends PostfixTemplate {
  protected StatementPostfixTemplateBase(String name, String description, String example) {
    super(name, description, example);
  }

  protected void surroundWith(PsiElement context, Editor editor, String text) {
    PsiExpression expr = getTopmostExpression(context);
    PsiElement parent = expr != null ? expr.getParent() : null;
    if (!(parent instanceof PsiExpressionStatement)) return;

    Project project = context.getProject();
    PsiElementFactory factory = JavaPsiFacade.getInstance(project).getElementFactory();
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(project);
    PsiElement statement = codeStyleManager.reformat(factory.createStatementFromText(text + " (" + expr.getText() + ") {\nst;\n}", context));
    statement = parent.replace(statement);

    //noinspection ConstantConditions
    PsiCodeBlock block = CodeInsightUtilCore.forcePsiPostprocessAndRestoreElement(PsiTreeUtil.getChildOfType(statement, PsiCodeBlock.class));
    TextRange range = block.getStatements()[0].getTextRange();
    editor.getDocument().deleteString(range.getStartOffset(), range.getEndOffset());

    editor.getCaretModel().moveToOffset(range.getStartOffset());
  }
}
