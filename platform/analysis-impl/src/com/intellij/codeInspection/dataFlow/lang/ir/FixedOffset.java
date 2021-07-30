// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

public class FixedOffset extends ControlFlowOffset {
  private final int myOffset;

  public FixedOffset(int offset) {
    myOffset = offset;
  }

  @Override
  public int getInstructionOffset() {
    return myOffset;
  }
}
