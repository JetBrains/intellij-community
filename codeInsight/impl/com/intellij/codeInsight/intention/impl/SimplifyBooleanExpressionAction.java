/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;

public class SimplifyBooleanExpressionAction implements IntentionAction{
  public String getText() {
    return getFamilyName();
  }

  public String getFamilyName() {
    return new SimplifyBooleanExpressionFix(null,null).getFamilyName();
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    PsiExpression expression = getExpressionToSimplify(editor, file);
    return expression != null && SimplifyBooleanExpressionFix.canBeSimplified(expression);
  }

  private static PsiExpression getExpressionToSimplify(final Editor editor, final PsiFile file) {
    int offset = editor.getCaretModel().getOffset();
    PsiElement element = file.findElementAt(offset);
    if (element == null) return null;
    PsiExpression expression = PsiTreeUtil.getParentOfType(element, PsiExpression.class);
    if (expression == null) return null;
    while (expression.getParent() instanceof PsiExpression) {
      expression = (PsiExpression)expression.getParent();
    }
    return expression;
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;
    PsiExpression expression = getExpressionToSimplify(editor, file);
    SimplifyBooleanExpressionFix.simplifyExpression(expression);
  }

  public boolean startInWriteAction() {
    return true;
  }
}