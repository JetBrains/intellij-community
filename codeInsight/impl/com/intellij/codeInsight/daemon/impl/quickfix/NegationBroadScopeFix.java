package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * @author cdr
 * if (!a == b) ...  =>  if (!(a == b)) ...
 */

public class NegationBroadScopeFix implements IntentionAction {
  private final PsiPrefixExpression myPrefixExpression;

  public NegationBroadScopeFix(PsiPrefixExpression prefixExpression) {
    myPrefixExpression = prefixExpression;
  }

  public String getText() {
    String text = myPrefixExpression.getOperand().getText();
    text += " ";
    PsiElement parent = myPrefixExpression.getParent();
    String operation = parent instanceof PsiInstanceOfExpression
                       ? PsiKeyword.INSTANCEOF
                       : ((PsiBinaryExpression)parent).getOperationSign().getText();
    text += operation + " ";

    String rop;
    if (parent instanceof PsiInstanceOfExpression) {
      final PsiTypeElement type = ((PsiInstanceOfExpression)parent).getCheckType();
      rop = type == null ? "" : type.getText();
    }
    else {
      final PsiExpression rOperand = ((PsiBinaryExpression)parent).getROperand();
      rop = rOperand == null ? "" : rOperand.getText();
    }

    text += rop;
    return QuickFixBundle.message("negation.broader.scope.text", text);
  }

  public String getFamilyName() {
    return QuickFixBundle.message("negation.broader.scope.family");
  }

  public boolean isAvailable(Project project, Editor editor, PsiFile file) {
    if (myPrefixExpression == null || !myPrefixExpression.isValid()) return false;

    PsiElement parent = myPrefixExpression.getParent();
    if (parent instanceof PsiInstanceOfExpression && ((PsiInstanceOfExpression)parent).getOperand() == myPrefixExpression) {
      return true;
    }
    if (!(parent instanceof PsiBinaryExpression)) return false;
    PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
    return binaryExpression.getLOperand() == myPrefixExpression && TypeConversionUtil.isBooleanType(binaryExpression.getType());
  }

  public void invoke(Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    PsiExpression operand = myPrefixExpression.getOperand();
    PsiElement unnegated = myPrefixExpression.replace(operand);
    PsiElement parent = unnegated.getParent();
    PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();

    PsiPrefixExpression negated = (PsiPrefixExpression)factory.createExpressionFromText("!(xxx)", parent);
    PsiParenthesizedExpression parentheses = (PsiParenthesizedExpression)negated.getOperand();
    parentheses.getExpression().replace(parent.copy());
    parent.replace(negated);
  }

  public boolean startInWriteAction() {
    return true;
  }
}
