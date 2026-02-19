// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine;

import com.intellij.debugger.SourcePosition;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public interface BreakpointStepMethodFilter extends MethodFilter {
  @Nullable
  SourcePosition getBreakpointPosition();

  /**
   * @return a zero-based line number of the last lambda statement, or -1 if not available
   */
  int getLastStatementLine();
}
