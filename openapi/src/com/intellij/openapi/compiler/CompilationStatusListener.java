/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

import java.util.EventListener;

/**
 * A listener for compiler events
 * @see CompilerManager
 */
public interface CompilationStatusListener extends EventListener{
  /**
   * Invoked in a Swing dispatch thread after the compilation is finished
   * @param aborted true if compilatioin has been cancelled
   * @param errors error count
   * @param warnings warning count
   */
  void compilationFinished(boolean aborted, int errors, int warnings);
}
