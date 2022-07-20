// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.compiler

/**
 * A callback interface passed to ComplerManager methods. Provides notification similar to
 * [CompilationStatusListener].
 *
 * @see CompilerManager.compile
 */
interface CompileStatusNotification {
  /**
   * Invoked in a Swing dispatch thread after the compilation is finished.
   *
   * @param aborted  true if compilation has been cancelled.
   * @param errors   error count
   * @param warnings warning count
   * @param compileContext context for the finished compilation
   */
  fun finished(aborted: Boolean, errors: Int, warnings: Int, compileContext: CompileContext)
}