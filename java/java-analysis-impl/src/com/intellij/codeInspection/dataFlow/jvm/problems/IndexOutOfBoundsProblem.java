// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.jvm.problems;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.UnsatisfiedConditionProblem;
import com.intellij.codeInspection.dataFlow.lang.ir.DfaInstructionState;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.value.*;
import com.intellij.util.ThreeState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.codeInspection.dataFlow.types.DfTypes.intValue;

public interface IndexOutOfBoundsProblem extends UnsatisfiedConditionProblem {
  /**
   * @return a descriptor that describes the length that the index should not exceed.
   * An index must be not less than zero and less than the length.
   */
  @NotNull DerivedVariableDescriptor getLengthDescriptor();

  private boolean applyBoundsCheck(@NotNull DfaMemoryState memState,
                                  @NotNull DfaValue array,
                                  @NotNull DfaValue index) {
    DfaValueFactory factory = index.getFactory();
    DfaValue length = getLengthDescriptor().createValue(factory, array);
    DfaCondition lengthMoreThanZero = length.cond(RelationType.GT, intValue(0));
    if (!memState.applyCondition(lengthMoreThanZero)) return false;
    DfaCondition indexNonNegative = index.cond(RelationType.GE, intValue(0));
    if (!memState.applyCondition(indexNonNegative)) return false;
    DfaCondition indexLessThanLength = index.cond(RelationType.LT, length);
    if (!memState.applyCondition(indexLessThanLength)) return false;
    return true;
  }

  default DfaInstructionState @Nullable [] processOutOfBounds(@NotNull DataFlowInterpreter interpreter,
                                                              @NotNull DfaMemoryState stateBefore,
                                                              @NotNull DfaValue index,
                                                              @NotNull DfaValue array,
                                                              @Nullable DfaControlTransferValue outOfBoundsTransfer) {
    boolean alwaysOutOfBounds = !applyBoundsCheck(stateBefore, array, index);
    ThreeState failed = alwaysOutOfBounds ? ThreeState.YES : ThreeState.UNSURE;
    interpreter.getListener().onCondition(this, index, failed, stateBefore);
    if (alwaysOutOfBounds) {
      if (outOfBoundsTransfer != null) {
        List<DfaInstructionState> states = outOfBoundsTransfer.dispatch(stateBefore, interpreter);
        for (DfaInstructionState state : states) {
          state.getMemoryState().markEphemeral();
        }
        return states.toArray(DfaInstructionState.EMPTY_ARRAY);
      }
      return DfaInstructionState.EMPTY_ARRAY;
    }
    return null;
  }
}
