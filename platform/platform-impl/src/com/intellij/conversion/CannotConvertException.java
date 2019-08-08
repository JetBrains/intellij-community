// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.conversion;

import org.jetbrains.annotations.NotNull;

/**
 * @author nik
 */
public final class CannotConvertException extends Exception {
  public CannotConvertException(@NotNull String message) {
    super(message);
  }

  public CannotConvertException(@NotNull String message, Throwable cause) {
    super(message, cause);
  }
}
