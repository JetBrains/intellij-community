// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist;

import com.intellij.codeInspection.dataFlow.TypeConstraint;
import com.intellij.codeInspection.dataFlow.TypeConstraints;
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue;
import com.intellij.debugger.engine.evaluation.EvaluateException;
import com.intellij.debugger.jdi.StackFrameProxyEx;
import com.intellij.lang.LanguageExtension;
import com.intellij.psi.PsiElement;
import com.sun.jdi.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Language specific helpers to implement DFAAssist (for JVM languages only)
 */
public interface DfaAssistProvider {
  LanguageExtension<DfaAssistProvider> EP_NAME = new LanguageExtension<>("com.intellij.debugger.dfaAssistProvider");

  /**
   * A sentinel to represent null constant.
   *
   * @see #getJdiValueForDfaVariable(StackFrameProxyEx, DfaVariableValue, PsiElement)
   */
  Value NullConst = new Value() {
    @Override
    public VirtualMachine virtualMachine() { return null; }

    @Override
    public Type type() { return null; }

    @Override
    public String toString() { return "null"; }
  };

  /**
   * Represents a 'virtual' boxed value which in fact does not exist in the VM memory.
   * Can be useful to virtually undo optimizations like instantiation of inline function generic parameter with a primitive value in Kotlin.
   *
   * @param value a primitive value to box
   * @param type type of the box (e.g. {@code java.lang.Integer})
   */
  record BoxedValue(@NotNull Value value, @NotNull ReferenceType type) implements Value {
    @Override
    public VirtualMachine virtualMachine() {
      return value.virtualMachine();
    }
  }

  /**
   * Quick check whether code location matches the source code in the editor
   *
   * @param element  PsiElement in the editor
   * @param location location reported by debugger
   * @return true if debugger location likely matches to the editor location;
   * false if definitely doesn't match (in this case, DFA Assist will be turned off)
   */
  boolean locationMatches(@NotNull PsiElement element, @NotNull Location location);

  /**
   * @param element psi element the debugger state points at
   * @return PSI anchor where DFA interpretation should start (likely, beginning of statement).
   * Currently, at this anchor interpretation stack must be empty. May return null if DFA assist cannot start
   * at current context.
   */
  @Nullable PsiElement getAnchor(@NotNull PsiElement element);

  /**
   * @param anchor anchor returned by {@link #getAnchor(PsiElement)} call
   * @return code block to analyze via DFA assist (e.g., method body, loop body, etc.)
   */
  @Nullable PsiElement getCodeBlock(@NotNull PsiElement anchor);

  /**
   * @param proxy  proxy to create JDI values
   * @param var    DfaVariableValue to find value for
   * @param anchor anchor previously returned by {@link #getAnchor(PsiElement)} call, where analysis takes place
   * @return JDI value for a variable; null if value is not known; NullConst if value is known to be null
   * (use {@link #wrap(Value)} utility method for this purpose).
   * @throws EvaluateException if proxy throws
   */
  @Nullable Value getJdiValueForDfaVariable(@NotNull StackFrameProxyEx proxy,
                                            @NotNull DfaVariableValue var,
                                            @NotNull PsiElement anchor) throws EvaluateException;

  /**
   * @return a new listener to attach to DFA session that will gather DFAAssist hints
   */
  @NotNull DebuggerDfaListener createListener();

  /**
   * @param anchor       a context PsiElement previously returned by {@link #getAnchor(PsiElement)}
   * @param jvmClassName JVM class name like "java/lang/String"
   * @return a {@link TypeConstraint} suitable for the current language;
   * {@link TypeConstraints#TOP} if class is not resolved
   */
  @NotNull TypeConstraint constraintFromJvmClassName(@NotNull PsiElement anchor, @NotNull String jvmClassName);

  /**
   * A helper method to implement {@link #getJdiValueForDfaVariable(StackFrameProxyEx, DfaVariableValue, PsiElement)}
   *
   * @param value value to wrap
   * @return NullConst if value is null; value otherwise
   */
  static @NotNull Value wrap(@Nullable Value value) {
    return value == null ? NullConst : value;
  }
}
