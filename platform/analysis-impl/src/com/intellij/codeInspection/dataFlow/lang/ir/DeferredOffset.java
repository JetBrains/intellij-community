// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.dataFlow.lang.ir;

public class DeferredOffset extends ControlFlowOffset {
  private int myOffset = -1;

  @Override
  public int getInstructionOffset() {
    if (myOffset == -1) {
      throw new IllegalStateException("Not set");
    }
    return myOffset;
  }

  public void setOffset(int offset) {
    if (myOffset != -1) {
      throw new IllegalStateException("Already set");
    }
    else {
      myOffset = offset;
    }
  }

  @Override
  public String toString() {
    return myOffset == -1 ? "<not set>" : super.toString();
  }
}
