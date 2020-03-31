// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.passwordSafe;

/**
 * @deprecated unused, left for binary compatibility
 */
@SuppressWarnings("unused")
@Deprecated
public class PasswordSafeException extends RuntimeException {
  public PasswordSafeException(String message, Throwable cause) {
    super(message, cause);
  }

  public PasswordSafeException(String message) {
    super(message);
  }
}
