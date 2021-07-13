// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

public abstract class ControlFlowOffset {
  public abstract int getInstructionOffset();

  @Override
  public String toString() {
    return String.valueOf(getInstructionOffset());
  }
}
