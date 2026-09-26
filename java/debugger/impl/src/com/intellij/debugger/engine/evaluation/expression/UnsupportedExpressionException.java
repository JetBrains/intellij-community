// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.engine.evaluation.EvaluateException;

public class UnsupportedExpressionException extends EvaluateException {
  public UnsupportedExpressionException(String message) {
    super(message);
  }
}
