// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.unwrap;

import com.intellij.codeInsight.CodeInsightBundle;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiPolyadicExpression;
import com.intellij.psi.PsiVariable;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class JavaPolyadicExpressionUnwrapper extends JavaUnwrapper {
  public JavaPolyadicExpressionUnwrapper() {
    super("");
  }

  @Override
  public @NotNull String getDescription(@NotNull PsiElement e) {
    return CodeInsightBundle.message("unwrap.with.placeholder", e.getText());
  }

  @Override
  public boolean isApplicableTo(@NotNull PsiElement e) {
    if (!(e.getParent() instanceof PsiPolyadicExpression expression)) {
      return false;
    }

    final PsiExpression operand = findOperand(e, expression);
    return operand != null;
  }

  @Override
  protected void doUnwrap(PsiElement element, Context context) throws IncorrectOperationException {
    final PsiPolyadicExpression parent = (PsiPolyadicExpression)element.getParent();

    final PsiExpression operand = findOperand(element, parent);

    if (operand == null) {
      return;
    }

    context.extractElement(operand, parent);
    if (parent.getParent() instanceof PsiVariable) {
      context.deleteExactly(parent);
    }
    else {
      context.delete(parent);
    }
  }

  @Override
  public PsiElement collectAffectedElements(@NotNull PsiElement e, @NotNull List<? super PsiElement> toExtract) {
    super.collectAffectedElements(e, toExtract);
    return e.getParent();
  }

  private static @Nullable PsiExpression findOperand(@NotNull PsiElement e, @NotNull PsiPolyadicExpression expression) {
    final TextRange elementTextRange = e.getTextRange();

    for (PsiExpression operand : expression.getOperands()) {
      final TextRange operandTextRange = operand.getTextRange();
      if (operandTextRange != null && operandTextRange.contains(elementTextRange)) {
        return operand;
      }
    }
    return null;
  }
}
