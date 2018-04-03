// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.bytecodeAnalysis;

class TooComplexException extends RuntimeException {
  TooComplexException(Member method, int steps) {
    super("limit is reached, steps: " + steps + " in method " + method);
  }

  static void check(Member method, int steps) {
    if(steps >= Analysis.STEPS_LIMIT) {
      throw new TooComplexException(method, steps);
    }
  }
}
