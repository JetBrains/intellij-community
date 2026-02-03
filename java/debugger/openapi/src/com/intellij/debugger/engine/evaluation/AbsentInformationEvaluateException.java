// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.engine.evaluation;

public class AbsentInformationEvaluateException extends EvaluateException {
  public AbsentInformationEvaluateException(String msg, Throwable th) {
    super(msg, th);
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
