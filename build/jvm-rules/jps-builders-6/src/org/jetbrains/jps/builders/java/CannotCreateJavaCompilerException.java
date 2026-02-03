// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.builders.java;

public final class CannotCreateJavaCompilerException extends Exception {
  public CannotCreateJavaCompilerException(String message) {
    super(message);
  }
}
