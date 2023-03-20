// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.patterns;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;

public class PsiBinaryExpressionPattern extends PsiExpressionPattern<PsiBinaryExpression, PsiBinaryExpressionPattern>{
  protected PsiBinaryExpressionPattern() {
    super(PsiBinaryExpression.class);
  }

  public PsiBinaryExpressionPattern left(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>("left") {
      @Override
      public boolean accepts(@NotNull final PsiBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getLOperand(), context);
      }
    });
  }

  public PsiBinaryExpressionPattern right(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>("right") {
      @Override
      public boolean accepts(@NotNull final PsiBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getROperand(), context);
      }
    });
  }

  public PsiBinaryExpressionPattern operation(final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>("operation") {
      @Override
      public boolean accepts(@NotNull final PsiBinaryExpression psiBinaryExpression, final ProcessingContext context) {
        return pattern.accepts(psiBinaryExpression.getOperationSign(), context);
      }
    });
  }

}
