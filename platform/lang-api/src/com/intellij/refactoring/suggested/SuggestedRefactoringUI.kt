// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.psi.PsiCodeFragment
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution.NewParameterValue
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature

/**
 * A service providing information required for building user-interface of Change Signature refactoring.
 */
abstract class SuggestedRefactoringUI {
  /**
   * Creates an instance of helper [SignaturePresentationBuilder] class which is used to build [SignatureChangePresentationModel].
   * @param signature signature (either old or new) to build presentation for
   * @param otherSignature other signature (either new or old)
   * @param isOldSignature true if [signature] represents the old signature, or false otherwise
   */
  abstract fun createSignaturePresentationBuilder(
    signature: Signature,
    otherSignature: Signature,
    isOldSignature: Boolean
  ): SignaturePresentationBuilder

  open fun buildSignatureChangePresentation(oldSignature: Signature, newSignature: Signature): SignatureChangePresentationModel {
    val oldSignaturePresentation = createSignaturePresentationBuilder(oldSignature, newSignature, isOldSignature = true)
      .apply { buildPresentation() }
      .result

    val newSignaturePresentation = createSignaturePresentationBuilder(newSignature, oldSignature, isOldSignature = false)
      .apply { buildPresentation() }
      .result

    val model = SignatureChangePresentationModel(oldSignaturePresentation, newSignaturePresentation)

    return model.improvePresentation()
  }

  data class NewParameterData(
    val presentableName: String,
    val valueFragment: PsiCodeFragment,
    val offerToUseAnyVariable: Boolean
  )

  /**
   * Extracts data about new parameters to offer the user to specify its values for updating calls.
   */
  abstract fun extractNewParameterData(data: SuggestedChangeSignatureData): List<NewParameterData>

  /**
   * Extracts value for a new parameter from code fragment after its editing by the user.
   * @return entered expression or *null* if no expression in the code fragment
   */
  abstract fun extractValue(fragment: PsiCodeFragment): NewParameterValue.Expression?

  /**
   * Use this implementation of [SuggestedRefactoringUI], if only Rename refactoring is supported for the language.
   */
  object RenameOnly : SuggestedRefactoringUI() {
    override fun createSignaturePresentationBuilder(
      signature: Signature,
      otherSignature: Signature,
      isOldSignature: Boolean
    ): SignaturePresentationBuilder {
      throw UnsupportedOperationException()
    }

    override fun extractNewParameterData(data: SuggestedChangeSignatureData): List<NewParameterData> {
      throw UnsupportedOperationException()
    }

    override fun extractValue(fragment: PsiCodeFragment): NewParameterValue.Expression? {
      throw UnsupportedOperationException()
    }

  }
}