// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.controlflow.impl;

import com.intellij.codeInsight.controlflow.ControlFlow;
import com.intellij.codeInsight.controlflow.Instruction;
import org.jetbrains.annotations.NotNull;

public class ControlFlowImpl  implements ControlFlow {
  private final Instruction[] myInstructions;

  public ControlFlowImpl(Instruction @NotNull [] instructions) {
    myInstructions = instructions;
  }

  @Override
  public Instruction @NotNull [] getInstructions() {
    return myInstructions;
  }
}
