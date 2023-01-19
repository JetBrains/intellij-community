// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.compiler;

import org.jetbrains.annotations.NotNull;

/**
 * A callback interface passed to ComplerManager methods. Provides notification similar to
 * {@link CompilationStatusListener}.
 *
 * @see CompilerManager#compile(CompileScope, CompileStatusNotification)
 */
public interface CompileStatusNotification {
  /**
   * Invoked in a Swing dispatch thread after the compilation is finished.
   *
   * @param aborted  true if compilation has been cancelled.
   * @param errors   error count
   * @param warnings warning count
   * @param compileContext context for the finished compilation
   */
  void finished(boolean aborted, int errors, int warnings, @NotNull CompileContext compileContext);
}