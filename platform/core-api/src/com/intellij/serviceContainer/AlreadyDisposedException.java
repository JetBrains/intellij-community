// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.progress.ProcessCanceledException;
import org.jetbrains.annotations.NotNull;

/**
 * @see Disposable
 */
public final class AlreadyDisposedException extends ProcessCanceledException {
  public AlreadyDisposedException(@NotNull String message) {
    super(message);
  }
}
