/*
 * Copyright (c) 2000-2004 by JetBrains s.r.o. All Rights Reserved.
 * Use is subject to license terms.
 */
package com.intellij.openapi.compiler;

public interface Compiler {
  /**
   * @return A non-null string. All registered compilers should have unique description
   */
  String getDescription();
  /**
   * Called before compilation starts. If at least one of registered compilers returned false, compilation won't start.
   * @return true if everything is ok, false otherwise
   * @param scope
   */
  boolean validateConfiguration(CompileScope scope);
}
