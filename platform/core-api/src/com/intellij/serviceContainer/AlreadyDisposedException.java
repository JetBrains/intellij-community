// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer;

import com.intellij.openapi.Disposable;
import org.jetbrains.annotations.NotNull;

/**
 * @see Disposable
 */
public final class AlreadyDisposedException extends IllegalStateException {
  public AlreadyDisposedException(@NotNull String message) {
    super(message);
  }
}
