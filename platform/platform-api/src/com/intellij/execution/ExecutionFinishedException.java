// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution;

import com.intellij.ide.IdeBundle;

public class ExecutionFinishedException extends ExecutionException {
  public ExecutionFinishedException() {
    this(null);
  }

  public ExecutionFinishedException(Throwable cause) {
    super(cause == null || cause.getMessage() == null ? IdeBundle.message("dialog.message.execution.finished")
                                                      : IdeBundle.message("dialog.message.execution.finished.because", cause.getMessage()), cause);
  }
}
