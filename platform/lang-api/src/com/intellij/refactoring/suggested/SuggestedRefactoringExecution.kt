// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.psi.PsiElement

/**
 * A service performing actual changes in the code when user executes suggested refactoring.
 */
abstract class SuggestedRefactoringExecution(protected val refactoringSupport: SuggestedRefactoringSupport) {
  /**
   * Prepares data for Change Signature refactoring while the declaration has state with user changes (the new signature).
   */
  abstract fun prepareChangeSignature(data: SuggestedChangeSignatureData): Any?

  /**
   * Performs Change Signature refactoring. This method is invoked with the declaration reverted to its original state
   * before user changes (the old signature). The list of imports is also reverted to the original state.
   */
  abstract fun performChangeSignature(data: SuggestedChangeSignatureData, newParameterValues: List<NewParameterValue>, preparedData: Any?)

  /**
   * Use this implementation of [SuggestedRefactoringExecution], if only Rename refactoring is supported for the language.
   */
  open class RenameOnly(refactoringSupport: SuggestedRefactoringSupport) : SuggestedRefactoringExecution(refactoringSupport) {
    override fun performChangeSignature(
      data: SuggestedChangeSignatureData,
      newParameterValues: List<NewParameterValue>,
      preparedData: Any?
    ) {
      throw UnsupportedOperationException()
    }

    override fun prepareChangeSignature(data: SuggestedChangeSignatureData): Any? {
      throw UnsupportedOperationException()
    }
  }

  /**
   * Class representing value for a new parameter to be used for updating arguments of calls.
   */
  sealed class NewParameterValue {
    object None : NewParameterValue()
    data class Expression(val expression: PsiElement) : NewParameterValue()
    object AnyVariable : NewParameterValue()
  }
}