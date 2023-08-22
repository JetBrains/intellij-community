// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.jps.javac;

/**
 * @author Eugene Zhuravlev
 */
final class CompilationCanceledException extends RuntimeException{
  CompilationCanceledException() {
    super("Compilation canceled");
  }

  @Override
  public synchronized Throwable fillInStackTrace() {
    return this;
  }
}
