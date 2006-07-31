/**
 * @author cdr
 */
package com.intellij.codeInsight.intention.impl;

import com.intellij.codeInsight.daemon.impl.quickfix.SimplifyBooleanExpressionFix;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class SimplifyBooleanExpressionAction implements IntentionAction{
  @NotNull
  public String getText() {
    return getFamilyName();
  }

  @NotNull
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
    PsiElement parent = expression;
    while (parent instanceof PsiExpression && (((PsiExpression)parent).getType() == PsiType.BOOLEAN || parent instanceof PsiConditionalExpression)) {
      expression = (PsiExpression)parent;
      parent = parent.getParent();
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