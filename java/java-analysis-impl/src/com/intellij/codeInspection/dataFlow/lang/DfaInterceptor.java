// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang;

import com.intellij.codeInspection.dataFlow.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An interceptor that can peek into DFA analysis intermediate states and do something with them
 */
public interface DfaInterceptor {
  /**
   * Called before a value is being pushed to the memory state stack during symbolic interpretation.
   * The value with the same anchor can be pushed many times to the different memory states.
   * Only values that have an anchor are reported here.
   *
   * @param args   input arguments used to evaluate the value
   * @param value  a value being pushed
   * @param anchor an anchor that describes the location of the pushed value
   * @param state  a memory state where expression is about to be pushed
   */
  default void beforePush(@NotNull DfaValue @NotNull [] args,
                          @NotNull DfaValue value,
                          @NotNull DfaAnchor anchor,
                          @NotNull DfaMemoryState state) {

  }

  /**
   * Called for every expression that fails to satisfy the condition required by EnsureInstruction.
   * Note that it can be called for the same place several times (once per memory state).
   * @param problem a problem descriptor
   * @param value value that was checked
   * @param failed YES if condition failed always; NO if it's satisfied; UNSURE if it may fail.
   * @param state memory state
   */
  default void onCondition(@NotNull UnsatisfiedConditionProblem problem,
                           @NotNull DfaValue value,
                           @NotNull ThreeState failed,
                           @NotNull DfaMemoryState state) {

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
                                @Nullable DfaAnchor anchor) {

  }
}
