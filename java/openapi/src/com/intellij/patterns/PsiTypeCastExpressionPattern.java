package com.intellij.patterns;

import com.intellij.psi.PsiExpression;
import com.intellij.psi.PsiTypeCastExpression;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public class PsiTypeCastExpressionPattern extends PsiExpressionPattern<PsiTypeCastExpression, PsiTypeCastExpressionPattern> {
  PsiTypeCastExpressionPattern() {
    super(PsiTypeCastExpression.class);
  }

  public PsiTypeCastExpressionPattern withOperand(final ElementPattern<? extends PsiExpression> operand) {
    return with(new PatternCondition<PsiTypeCastExpression>("withOperand") {
      @Override
      public boolean accepts(@NotNull PsiTypeCastExpression psiTypeCastExpression, ProcessingContext context) {
        return operand.accepts(psiTypeCastExpression.getOperand(), context);
      }
    });
  }
}
