// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir.inst;

import com.intellij.codeInspection.dataFlow.lang.DfaAnchor;
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
  @Nullable
  public DfaAnchor getDfaAnchor() {
    return myAnchor;
  }
}
