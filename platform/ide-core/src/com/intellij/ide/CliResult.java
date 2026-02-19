// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide;

import com.intellij.openapi.util.NlsContexts;
import org.jetbrains.annotations.Nullable;

public record CliResult(int exitCode, @Nullable @NlsContexts.DialogMessage String message) {
  public static final CliResult OK = new CliResult(0, null);

  @Override
  public String toString() {
    return message == null ? String.valueOf(exitCode) : exitCode + ": " + message;
  }
}
