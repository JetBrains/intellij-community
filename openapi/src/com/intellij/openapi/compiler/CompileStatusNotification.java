/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

/**
 * A callback interface passed to ComplerManager methods. Provides notification similar to CompilationStatusListener
 * @see CompilerManager
 * @see CompilationStatusListener
 */
public interface CompileStatusNotification {
  /**
   * Invoked in a Swing dispatch thread after the compilation is finished
   * @param aborted true if compilatioin has been cancelled
   * @param errors error count
   * @param warnings warning count
   */
  void finished(boolean aborted, int errors, int warnings);
}
