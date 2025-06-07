// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.psi.controlFlow;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public abstract class InstructionBase implements Instruction, Cloneable{
  @Override
  public @NotNull Instruction clone() {
    try {
      return (Instruction)super.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public abstract @NonNls String toString();

}