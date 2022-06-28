// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * if (!a == b) ...  =>  if (!(a == b)) ...
 */
public class NegationBroadScopeFix implements IntentionAction {
  private final PsiPrefixExpression myPrefixExpression;

  public NegationBroadScopeFix(@NotNull PsiPrefixExpression prefixExpression) {
    myPrefixExpression = prefixExpression;
  }

  @Override
  public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
    return new NegationBroadScopeFix(PsiTreeUtil.findSameElementInCopy(myPrefixExpression, target));
  }

  @Override
  @NotNull
  public String getText() {
    PsiExpression operand = myPrefixExpression.getOperand();
    String text = operand == null ? "" : operand.getText() + " ";
    PsiElement parent = myPrefixExpression.getParent();

    String rop;
    if (parent instanceof PsiInstanceOfExpression) {
      text += PsiKeyword.INSTANCEOF + " ";
      final PsiTypeElement typeElement = ((PsiInstanceOfExpression)parent).getCheckType();
      rop = typeElement == null ? "" : typeElement.getText();
    }
    else if (parent instanceof PsiBinaryExpression) {
      text += ((PsiBinaryExpression)parent).getOperationSign().getText() + " ";
      final PsiExpression rOperand = ((PsiBinaryExpression)parent).getROperand();
      rop = rOperand == null ? "" : rOperand.getText();
    }
    else {
      rop = "<expr>";
    }

    text += rop;
    return QuickFixBundle.message("negation.broader.scope.text", text);
  }

  @Override
  @NotNull
  public String getFamilyName() {
    return QuickFixBundle.message("negation.broader.scope.family");
  }

  @Override
  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    if (!myPrefixExpression.isValid() || myPrefixExpression.getOperand() == null) return false;

    PsiElement parent = myPrefixExpression.getParent();
    if (parent instanceof PsiInstanceOfExpression && ((PsiInstanceOfExpression)parent).getOperand() == myPrefixExpression) {
      return true;
    }
    if (!(parent instanceof PsiBinaryExpression)) return false;
    PsiBinaryExpression binaryExpression = (PsiBinaryExpression)parent;
    return binaryExpression.getLOperand() == myPrefixExpression && TypeConversionUtil.isBooleanType(binaryExpression.getType());
  }

  @NotNull
  @Override
  public PsiElement getElementToMakeWritable(@NotNull PsiFile file) {
    return myPrefixExpression.getContainingFile();
  }

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!isAvailable(project, editor, file)) return;
    PsiExpression operand = myPrefixExpression.getOperand();
    PsiElement unnegated = myPrefixExpression.replace(operand);
    PsiElement parent = unnegated.getParent();
    PsiElementFactory factory = JavaPsiFacade.getElementFactory(file.getProject());

    PsiPrefixExpression negated = (PsiPrefixExpression)factory.createExpressionFromText("!(xxx)", parent);
    PsiParenthesizedExpression parentheses = (PsiParenthesizedExpression)negated.getOperand();
    parentheses.getExpression().replace(parent.copy());
    parent.replace(negated);
  }

  @Override
  public boolean startInWriteAction() {
    return true;
  }
}
