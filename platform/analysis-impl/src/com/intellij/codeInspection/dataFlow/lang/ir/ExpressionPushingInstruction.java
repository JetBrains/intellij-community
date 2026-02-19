// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.dataFlow.lang.ir;

import com.intellij.codeInspection.dataFlow.interpreter.DataFlowInterpreter;
import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
import com.intellij.codeInspection.dataFlow.memory.DfaMemoryState;
import com.intellij.codeInspection.dataFlow.types.DfType;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * An instruction which pushes a result of evaluation to the stack and has an anchor
 */
public abstract class ExpressionPushingInstruction extends Instruction {
  private final @Nullable DfaAnchor myAnchor;

  protected ExpressionPushingInstruction(@Nullable DfaAnchor anchor) {
    myAnchor = anchor;
  }

  /**
   * @return a DfaAnchor that describes the value pushed to the stack, or null if this instruction is not bound to any particular anchor
   */
  public @Nullable DfaAnchor getDfaAnchor() {
    return myAnchor;
  }

  /**
   * Push result of this instruction to memory state stack (to be used by inheritors)
   * 
   * @param interpreter interpreter that interprets this instruction
   * @param state memory state to push to
   * @param value value to push (will be wrapped into {@link com.intellij.codeInspection.dataFlow.value.DfaTypeValue})
   * @param inputArgs input arguments used to calculate the resulting value
   */
  protected final void pushResult(@NotNull DataFlowInterpreter interpreter,
                                  @NotNull DfaMemoryState state,
                                  @NotNull DfType value,
                                  @NotNull DfaValue @NotNull ... inputArgs) {
    pushResult(interpreter, state, interpreter.getFactory().fromDfType(value), inputArgs);
  }

  /**
   * Push result of this instruction to memory state stack (to be used by inheritors)
   *
   * @param interpreter interpreter that interprets this instruction
   * @param state memory state to push to
   * @param value value to push
   * @param inputArgs input arguments used to calculate the resulting value
   */
  protected final void pushResult(@NotNull DataFlowInterpreter interpreter,
                                  @NotNull DfaMemoryState state,
                                  @NotNull DfaValue value,
                                  @NotNull DfaValue @NotNull ... inputArgs) {
    DfaAnchor anchor = getDfaAnchor();
    if (anchor != null) {
      interpreter.getListener().beforePush(inputArgs, value, anchor, state);
    }
    state.push(value);
  }
}
