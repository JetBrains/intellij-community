/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

/**
 * Describes a task to be executed before or after compilation
 * @see CompilerManager
 */
public interface CompileTask {
  /**
   * @param context current compile context
   * @return true if execution succeeded, false otherwise
   */
  boolean execute(CompileContext context);
}
