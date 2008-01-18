/*
 * Copyright (c) 2000-2007 JetBrains s.r.o. All Rights Reserved.
 */
package com.intellij.patterns;

import com.intellij.psi.PsiBinaryExpression;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.MatchingContext;
import com.intellij.patterns.TraverseContext;
import org.jetbrains.annotations.NotNull;

/**
 * @author peter
 */
public class PsiBinaryExpressionPattern extends PsiExpressionPattern<PsiBinaryExpression, PsiBinaryExpressionPattern>{
  protected PsiBinaryExpressionPattern() {
    super(PsiBinaryExpression.class);
  }

  public PsiBinaryExpressionPattern left(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>() {
      public boolean accepts(@NotNull final PsiBinaryExpression psiBinaryExpression, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.accepts(psiBinaryExpression.getLOperand(), matchingContext, traverseContext);
      }
    });
  }

  public PsiBinaryExpressionPattern right(@NotNull final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>() {
      public boolean accepts(@NotNull final PsiBinaryExpression psiBinaryExpression, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.accepts(psiBinaryExpression.getROperand(), matchingContext, traverseContext);
      }
    });
  }

  public PsiBinaryExpressionPattern operation(final ElementPattern pattern) {
    return with(new PatternCondition<PsiBinaryExpression>() {
      public boolean accepts(@NotNull final PsiBinaryExpression psiBinaryExpression, final MatchingContext matchingContext, @NotNull final TraverseContext traverseContext) {
        return pattern.accepts(psiBinaryExpression.getOperationSign(), matchingContext, traverseContext);
      }
    });
  }

}
