package com.intellij.psi.impl.source.tree.java;

import com.intellij.psi.JavaElementVisitor;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.source.Constants;
import org.jetbrains.annotations.NotNull;

public class PsiEmptyExpressionImpl extends ExpressionPsiElement implements PsiExpression{
  public PsiEmptyExpressionImpl() {
    super(Constants.EMPTY_EXPRESSION);
  }

  public PsiType getType() {
    return null;
  }

  public void accept(@NotNull PsiElementVisitor visitor) {
    if (visitor instanceof JavaElementVisitor) {
      ((JavaElementVisitor)visitor).visitExpression(this);
    }
    else {
      visitor.visitElement(this);
    }
  }

  public String toString() {
    return "PsiExpression(empty)";
  }
}
