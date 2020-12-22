// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.debugger.memory.agent;

import org.jetbrains.annotations.NotNull;

public class MemoryAgentActionResult<T> {
  public enum ErrorCode {
    OK,
    TIMEOUT;

    public static ErrorCode valueOf(int value) {
      return value == 0 ? OK : TIMEOUT;
    }
  }

  private final ErrorCode myErrorCode;
  private final T myResult;

  public MemoryAgentActionResult(@NotNull T result, @NotNull ErrorCode errorCode) {
    myErrorCode = errorCode;
    myResult = result;
  }

  @NotNull
  public T getResult() {
    return myResult;
  }

  public boolean executedSuccessfully() {
    return myErrorCode != ErrorCode.TIMEOUT;
  }
}
