// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.daemon.quickFix;

import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class IncorrectCreateFilePathException extends RuntimeException {
  public IncorrectCreateFilePathException(String message) {
    super(message);
  }
}
