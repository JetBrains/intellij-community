// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.evaluation.expression;

import com.intellij.debugger.JavaDebuggerBundle;
import com.intellij.debugger.engine.evaluation.EvaluateException;

public class BreakException extends EvaluateException {
  private final String myLabelName;

  public BreakException(String labelName) {
    super(JavaDebuggerBundle.message("evaluation.error.lebeled.loops.not.found", labelName), null);
    myLabelName = labelName;
  }

  public String getLabelName() {
    return myLabelName;
  }
}
