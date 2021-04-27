// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang;

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An interceptor that can peek into DFA analysis intermediate states and do something with them
 * @param <EXPR> type of expression element in the language
 */
public interface DfaInterceptor<EXPR extends PsiElement> {
  /**
   * Called before conditional goto instruction is executed that has an associated anchor
   * @param anchor a PSI anchor that associated with conditional goto instruction
   * @param isTrueBranch whether we are visiting the true branch. If both true and false jumps are possible,
   *                     the method will be repeatedly called with isTrueBranch equals to true and false.
   */
  default void beforeConditionalJump(@NotNull PsiElement anchor, boolean isTrueBranch) {
  }

  /**
   * Called before initializer end ({@link com.intellij.codeInspection.dataFlow.lang.ir.inst.EndOfInitializerInstruction}) is processed.
   * Both static and instance initializer are processed in the same flow.
   *
   * @param isStatic whether we are at the end of instance or static initializer
   * @param state memory state at this point
   */
  default void beforeInitializerEnd(boolean isStatic, @NotNull DfaMemoryState state) {
  }

  /**
   * Called before a PsiExpression result is being pushed to the memory state stack during symbolic interpretation.
   * The result of single expression can be pushed many times to the different memory states.
   *
   * @param value      a value being pushed
   * @param expression a physical PsiExpression which evaluates to given value.
   * @param range      if not-null, specifies a part of expression which corresponds to the value (like "a ^ b" range in "a ^ b ^ c" expression).
   * @param state      a memory state where expression is about to be pushed
   */
  default void beforeExpressionPush(@NotNull DfaValue value,
                                    @NotNull EXPR expression,
                                    @Nullable TextRange range,
                                    @NotNull DfaMemoryState state) {

  }

  /**
   * Called for every expression which corresponds to the method, method reference, or lambda result.
   *
   * @param value      expression value
   * @param expression an expression which produces given value. For conditional return like {@code return cond ? ifTrue : ifFalse;}
   *                   this method will be called for {@code ifTrue} and {@code ifFalse} separately. Could be null if the expression
   *                   is not readily available in the source code (e. g., for method references)
   * @param context    a context from which the result is returned (could be method, method reference, or lambda)
   * @param state      a memory state
   */
  default void beforeValueReturn(@NotNull DfaValue value,
                                 @Nullable EXPR expression,
                                 @NotNull PsiElement context,
                                 @NotNull DfaMemoryState state) {

  }

  /**
   * Called for every expression that fails to satisfy the condition required by EnsureInstruction.
   * Note that it can be called for the same place several times (once per memory state).
   * @param problem a problem descriptor
   * @param value top-of-stack value that failed the condition
   * @param failed YES if condition failed always; NO if it's satisfied; UNSURE if it may fail.
   */
  default void onCondition(@NotNull UnsatisfiedConditionProblem problem,
                           @NotNull DfaValue value,
                           @NotNull ThreeState failed) {

  }

  /**
   * Called for assignments
   *
   * @param source source value
   * @param dest target value
   * @param state memory state
   * @param anchor PSI anchor
   */
  default void beforeAssignment(@NotNull DfaValue source,
                                @NotNull DfaValue dest,
                                @NotNull DfaMemoryState state,
                                @Nullable PsiElement anchor) {

  }
}
