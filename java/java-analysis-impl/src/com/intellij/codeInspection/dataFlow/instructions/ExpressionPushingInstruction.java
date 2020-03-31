// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.instructions;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiExpression;
import org.jetbrains.annotations.Nullable;

/**
 * An instruction which pushes a result of {@link PsiExpression} (or its part) evaluation to the stack
 *
 */
public abstract class ExpressionPushingInstruction<T extends PsiExpression> extends Instruction {
  private final T myExpression;

  protected ExpressionPushingInstruction(T expression) {
    myExpression = expression;
  }
  
  /**
   * @return a PsiExpression which result is pushed to the stack, or null if this instruction is not bound to any particular PsiExpression
   */
  @Nullable
  public T getExpression() {
    return myExpression;
  }

  /**
   * @return if non-null, a part of PsiExpression, returned by {@link #getExpression()} which this instruction actually evaluates.
   * Usable for polyadic expressions like {@code a == b == c}: here instruction may evaluate only {@code a == b} part.
   */
  @Nullable
  public TextRange getExpressionRange() {return null;}
}
