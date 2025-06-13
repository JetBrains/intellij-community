// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.debugger.engine.dfaassist

import com.intellij.codeInspection.dataFlow.TypeConstraint
import com.intellij.codeInspection.dataFlow.value.DfaVariableValue
import com.intellij.debugger.engine.evaluation.EvaluateException
import com.intellij.debugger.jdi.StackFrameProxyEx
import com.intellij.lang.LanguageExtension
import com.intellij.psi.PsiElement
import com.sun.jdi.*
import org.jetbrains.annotations.ApiStatus

/**
 * Language specific helpers to implement DFAAssist (for JVM languages only)
 */
@ApiStatus.Internal
interface DfaAssistProvider {
  /**
   * Represents a 'virtual' boxed value which in fact does not exist in the VM memory.
   * Can be useful to virtually undo optimizations like instantiation of inline function generic parameter with a primitive value in Kotlin.
   *
   * @param value a primitive value to box
   * @param type type of the box (e.g. `java.lang.Integer`)
   */
  data class BoxedValue(val value: Value, val type: ReferenceType) : Value {
    override fun virtualMachine(): VirtualMachine? {
      return value.virtualMachine()
    }

    override fun type(): Type {
      return type
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
  suspend fun locationMatches(element: PsiElement, location: Location): Boolean

  /**
   * @param element psi element the debugger state points at
   * @return PSI anchor where DFA interpretation should start (likely, beginning of statement).
   * Currently, at this anchor interpretation stack must be empty. May return null if DFA assist cannot start
   * at current context.
   */
  fun getAnchor(element: PsiElement): PsiElement?

  /**
   * @param anchor anchor returned by [.getAnchor] call
   * @return code block to analyze via DFA assist (e.g., method body, loop body, etc.)
   */
  fun getCodeBlock(anchor: PsiElement): PsiElement?

  /**
   * @param proxy  proxy to create JDI values
   * @param dfaVar DfaVariableValue to find value for
   * @param anchor anchor previously returned by [.getAnchor] call, where analysis takes place
   * @return JDI value for a variable; null if value is not known; NullConst if value is known to be null
   * (use [.wrap] utility method for this purpose).
   * @throws EvaluateException if proxy throws
   */
  @Throws(EvaluateException::class)
  suspend fun getJdiValueForDfaVariable(
    proxy: StackFrameProxyEx,
    dfaVar: DfaVariableValue,
    anchor: PsiElement,
  ): Value?

  /**
   * @return a new listener to attach to DFA session that will gather DFAAssist hints
   */
  fun createListener(): DebuggerDfaListener

  /**
   * @param anchor       a context PsiElement previously returned by [.getAnchor]
   * @param jvmClassName JVM class name like "java/lang/String"
   * @return a [TypeConstraint] suitable for the current language;
   * [com.intellij.codeInspection.dataFlow.TypeConstraints.TOP] if class is not resolved
   */
  fun constraintFromJvmClassName(anchor: PsiElement, jvmClassName: String): TypeConstraint

  companion object {
    /**
     * A helper method to implement [.getJdiValueForDfaVariable]
     *
     * @param value value to wrap
     * @return NullConst if value is null; value otherwise
     */
    @JvmStatic
    fun wrap(value: Value?): Value {
      return value ?: NullConst
    }

    @JvmField
    val EP_NAME: LanguageExtension<DfaAssistProvider?> = LanguageExtension<DfaAssistProvider?>("com.intellij.debugger.dfaAssistProvider")

    /**
     * A sentinel to represent null constant.
     *
     * @see .getJdiValueForDfaVariable
     */
    @JvmField
    val NullConst: Value = object : Value {
      override fun virtualMachine(): VirtualMachine? {
        return null
      }

      override fun type(): Type? {
        return null
      }

      override fun toString(): String {
        return "null"
      }
    }
  }
}
