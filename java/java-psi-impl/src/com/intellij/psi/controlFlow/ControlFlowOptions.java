// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.controlFlow;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * Options for ControlFlow generation.
 * @see ControlFlowFactory
 * @see ControlFlowAnalyzer
 */
public class ControlFlowOptions {
  public static final @NotNull ControlFlowOptions NO_CONST_EVALUATE = new ControlFlowOptions(false, false);
  
  private final boolean myEnableShortCircuit;
  private final boolean myEvaluateConstantIfCondition;
  private final boolean myExceptionAfterAssignment;

  public ControlFlowOptions(boolean enableShortCircuit, boolean evaluateConstantIfCondition) {
    this(enableShortCircuit, evaluateConstantIfCondition, true);
  }

  public ControlFlowOptions(boolean enableShortCircuit, boolean evaluateConstantIfCondition, boolean exceptionAfterAssignment) {
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
           new ControlFlowOptions(myEnableShortCircuit, false, myExceptionAfterAssignment) : this;
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
    return Objects.hash(myEnableShortCircuit, myEvaluateConstantIfCondition, myExceptionAfterAssignment);
  }
}
