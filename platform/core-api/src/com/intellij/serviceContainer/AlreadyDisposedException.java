// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.serviceContainer;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;

/**
 * @see Disposable
 */
public final class AlreadyDisposedException extends CancellationException {
  public AlreadyDisposedException(@NotNull String message) {
    super(message);
  }
}
