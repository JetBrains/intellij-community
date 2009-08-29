/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiBinaryExpressionPattern extends PsiExpressionPattern<PsiBinaryExpression, PsiBinaryExpressionPattern>{
  protected PsiBinaryExpressionPattern() {
    super(PsiBinaryExpression.class);
  }

  public PsiBinaryExpressionPattern left(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>("left") {
      public boolean accepts(@NotNull final PsiBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.getCondition().accepts(psiBinaryExpression.getLOperand(), context);
      }
    });
  }

  public PsiBinaryExpressionPattern right(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>("right") {
      public boolean accepts(@NotNull final PsiBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.getCondition().accepts(psiBinaryExpression.getROperand(), context);
      }
    });
  }

  public PsiBinaryExpressionPattern operation(final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>("operation") {
      public boolean accepts(@NotNull final PsiBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.getCondition().accepts(psiBinaryExpression.getOperationSign(), context);
      }
    });
  }

}
