/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.java.codeInspection.bytecodeAnalysis.data;

import com.intellij.java.codeInspection.bytecodeAnalysis.ExpectContract;

public enum TestEnum {
  A, B, C;

  @ExpectContract(pure = true)
  public int getValue() {
    return ordinal()+1;
  }

  @ExpectContract(pure = true)
  public boolean isA() {
    switch (this) {
      case A:
        return true;
      default:
        return false;
    }
  }
}
