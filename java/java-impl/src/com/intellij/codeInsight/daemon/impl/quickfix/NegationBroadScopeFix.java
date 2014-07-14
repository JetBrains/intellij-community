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
package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.FileModificationService;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * @author cdr
 * if (!a == b) ...  =>  if (!(a == b)) ...
 */

public class NegationBroadScopeFix implements IntentionAction {
  private final PsiPrefixExpression myPrefixExpression;

  public NegationBroadScopeFix(@NotNull PsiPrefixExpression prefixExpression) {
    myPrefixExpression = prefixExpression;
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
      final PsiTypeElement type = ((PsiInstanceOfExpression)parent).getCheckType();
      rop = type == null ? "" : type.getText();
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

  @Override
  public void invoke(@NotNull Project project, Editor editor, PsiFile file) throws IncorrectOperationException {
    if (!isAvailable(project, editor, file) || !FileModificationService.getInstance().preparePsiElementForWrite(myPrefixExpression)) return;
    PsiExpression operand = myPrefixExpression.getOperand();
    PsiElement unnegated = myPrefixExpression.replace(operand);
    PsiElement parent = unnegated.getParent();
    PsiElementFactory factory = JavaPsiFacade.getInstance(file.getProject()).getElementFactory();

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
