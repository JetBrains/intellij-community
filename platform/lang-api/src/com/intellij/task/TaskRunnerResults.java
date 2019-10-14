// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.task;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Experimental
public enum TaskRunnerResults implements ProjectTaskRunner.Result {
  SUCCESS(false, false),
  FAILURE(false, true),
  ABORTED(true, false);

  private final boolean myAborted;
  private final boolean myErrors;

  TaskRunnerResults(boolean isAborted, boolean hasErrors) {
    myAborted = isAborted;
    myErrors = hasErrors;
  }

  @Override
  public boolean isAborted() {
    return myAborted;
  }

  @Override
  public boolean hasErrors() {
    return myErrors;
  }
}
