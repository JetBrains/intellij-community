// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.javac;

final class CompilationCanceledException extends RuntimeException{
  CompilationCanceledException() {
    super("Compilation canceled");
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
