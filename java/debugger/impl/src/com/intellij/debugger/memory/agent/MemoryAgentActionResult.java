// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.memory.agent;

import org.jetbrains.annotations.NotNull;

public class MemoryAgentActionResult<T> {
  public enum ErrorCode {
    OK, TIMEOUT, CANCELLED;

    public static ErrorCode valueOf(int value) {
      return switch (value) {
        case 0 -> OK;
        case 1 -> TIMEOUT;
        default -> CANCELLED;
      };
    }
  }

  private final ErrorCode myErrorCode;
  private final T myResult;

  public MemoryAgentActionResult(@NotNull T result, @NotNull ErrorCode errorCode) {
    myErrorCode = errorCode;
    myResult = result;
  }

  public @NotNull T getResult() {
    return myResult;
  }

  public boolean executedSuccessfully() {
    return myErrorCode == ErrorCode.OK;
  }
}
