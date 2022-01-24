// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.openapi.ui.ValidationInfo
import com.intellij.psi.PsiCodeFragment
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution.NewParameterValue
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

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

  data class NewParameterData @JvmOverloads constructor(
    @Nls val presentableName: String,
    val valueFragment: PsiCodeFragment,
    val offerToUseAnyVariable: Boolean,
    @Nls(capitalization = Nls.Capitalization.Sentence) val placeholderText: String? = null,
    val additionalData: NewParameterAdditionalData? = null,
    val suggestRename: Boolean = false
  )

  /**
   * Language-specific information to be stored in [NewParameterData].
   *
   * Don't put any PSI-related objects here.
   */
  interface NewParameterAdditionalData {
    override fun equals(other: Any?): Boolean
  }

  /**
   * Extracts data about new parameters to offer the user to specify its values for updating calls.
   */
  abstract fun extractNewParameterData(data: SuggestedChangeSignatureData): List<NewParameterData>

  /**
   * Validates value for [data] typed in [component].
   * This method should be very fast since it is called on any change for any parameter even if the updated parameter is not [data].
   */
  open fun validateValue(data: NewParameterData, component: JComponent?): ValidationInfo? = null

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