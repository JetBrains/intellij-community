// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.process.mediator.daemon.util;

import org.jetbrains.annotations.NotNull;

@FunctionalInterface
interface NativeCall {
  void run() throws NativeCallException, LinkageError;

  @SuppressWarnings("UseOfSystemOutOrSystemErr")
  static void tryRun(@NotNull NativeCall r, @NotNull String failureMessage) {
    try {
      r.run();
    }
    catch (NativeCallException | LinkageError e) {
      System.err.println(failureMessage + ": " + e.getMessage());
    }
  }

  class NativeCallException extends Exception {
    NativeCallException(@NotNull String message) {
      super(message);
    }
  }
}
