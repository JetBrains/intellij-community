// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.serviceContainer;

import org.jetbrains.annotations.NotNull;

public final class AlreadyDisposedException extends IllegalStateException {
  public AlreadyDisposedException(@NotNull String message) {
    super(message);
  }
}
