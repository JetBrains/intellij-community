// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.passwordSafe;

/** @deprecated unused, left for binary compatibility */
@SuppressWarnings("unused")
@Deprecated(forRemoval = true)
public class PasswordSafeException extends RuntimeException {
  public PasswordSafeException(String message, Throwable cause) {
    super(message, cause);
  }

  public PasswordSafeException(String message) {
    super(message);
  }
}
