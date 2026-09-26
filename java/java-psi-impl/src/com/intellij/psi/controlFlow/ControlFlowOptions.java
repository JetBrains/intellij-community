// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.controlFlow;

import org.jetbrains.annotations.NotNull;

/**
 * Options for ControlFlow generation.
 *
 * @see ControlFlowFactory
 * @see ControlFlowAnalyzer
 */
public class ControlFlowOptions {
  private static final ControlFlowOptions[] DATA = {
    new ControlFlowOptions(false, false, false),
    new ControlFlowOptions(false, false, true),
    new ControlFlowOptions(false, true, false),
    new ControlFlowOptions(false, true, true),
    new ControlFlowOptions(true, false, false),
    new ControlFlowOptions(true, false, true),
    new ControlFlowOptions(true, true, false),
    new ControlFlowOptions(true, true, true),
  };

  public static final @NotNull ControlFlowOptions NO_CONST_EVALUATE = create(false, false, true);

  private final boolean myEnableShortCircuit;
  private final boolean myEvaluateConstantIfCondition;
  private final boolean myExceptionAfterAssignment;

  public static ControlFlowOptions create(boolean enableShortCircuit,
                                          boolean evaluateConstantIfCondition,
                                          boolean exceptionAfterAssignment) {
    return DATA[(enableShortCircuit ? 4 : 0) +
                (evaluateConstantIfCondition ? 2 : 0) +
                (exceptionAfterAssignment ? 1 : 0)];
  }

  private ControlFlowOptions(boolean enableShortCircuit, boolean evaluateConstantIfCondition, boolean exceptionAfterAssignment) {
    myEnableShortCircuit = enableShortCircuit;
    myEvaluateConstantIfCondition = evaluateConstantIfCondition;
    myExceptionAfterAssignment = exceptionAfterAssignment;
  }

  /**
   * @return true if generate direct jumps for short-circuited operations,
   * e.g. jump to else branch of if statement after each calculation of '&&' operand in condition
   */
  public boolean enableShortCircuit() {
    return myEnableShortCircuit;
  }

  /**
   * @return true if evaluate constant expression inside 'if' statement condition and alter control flow accordingly
   * in case of unreachable statement analysis must be false
   */
  public boolean shouldEvaluateConstantIfCondition() {
    return myEvaluateConstantIfCondition;
  }

  /**
   * @return true if control flow assumes that exception could be thrown after assigment. 
   * True value is JLS-compatible, but might be counter-intuitive. 
   */
  public boolean isExceptionAfterAssignment() {
    return myExceptionAfterAssignment;
  }

  /**
   * @return ControlFlowOptions object that is the same as current but without "evaluate constant if condition" option.
   * @see #shouldEvaluateConstantIfCondition() 
   */
  public ControlFlowOptions dontEvaluateConstantIfCondition() {
    return myEvaluateConstantIfCondition ?
           create(myEnableShortCircuit, false, myExceptionAfterAssignment) : this;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ControlFlowOptions options = (ControlFlowOptions)o;
    return myEnableShortCircuit == options.myEnableShortCircuit &&
           myEvaluateConstantIfCondition == options.myEvaluateConstantIfCondition &&
           myExceptionAfterAssignment == options.myExceptionAfterAssignment;
  }

  @Override
  public int hashCode() {
    int result = 1;
    result = 31 * result + (myEnableShortCircuit ? 1231 : 1237);
    result = 31 * result + (myEvaluateConstantIfCondition ? 1231 : 1237);
    result = 31 * result + (myExceptionAfterAssignment ? 1231 : 1237);
    return result;
  }
}
