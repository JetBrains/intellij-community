// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.codeInspection.dataFlow.java.inst;

import com.intellij.codeInspection.dataFlow.lang.ir.Instruction;
import com.intellij.codeInspection.dataFlow.lang.ir.PushInstruction;
import com.intellij.codeInspection.dataFlow.value.DfaValue;
import com.intellij.codeInspection.dataFlow.value.DfaValueFactory;
import org.jetbrains.annotations.NotNull;

/**
 * An instruction that pushes given value to the stack for subsequent write via {@link AssignInstruction}
 * (it additionally processes escaping)
 */
public class JvmPushForWriteInstruction extends PushInstruction {
  public JvmPushForWriteInstruction(@NotNull DfaValue value) {
    super(value, null);
  }

  @Override
  public @NotNull Instruction bindToFactory(@NotNull DfaValueFactory factory) {
    return new JvmPushForWriteInstruction(getValue().bindToFactory(factory));
  }

  @Override
  public String toString() {
    return "PUSH_FOR_WRITE " + getValue();
  }
}
