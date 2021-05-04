// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

/*
 * @author max
 */
package com.intellij.codeInspection.dataFlow.interpreter;

public enum RunnerResult {
  /**
   * Successful completion
   */
  OK,
  /**
   * Method is too complex for analysis
   */
  TOO_COMPLEX,
  /**
   * Cannot analyze (probably method in severely incomplete)
   */
  NOT_APPLICABLE,
  /**
   * Analysis is explicitly cancelled via {@link DataFlowInterpreter#cancel()}
   */
  CANCELLED,
  /**
   * Aborted due to some internal error like corrupted stack
   */
  ABORTED
}