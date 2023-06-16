// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.refactoring.suggested

import com.intellij.refactoring.suggested.SignatureChangePresentationModel.Effect
import com.intellij.refactoring.suggested.SignatureChangePresentationModel.TextFragment
import com.intellij.refactoring.suggested.SignatureChangePresentationModel.TextFragment.*
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Parameter
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature

typealias ParameterFragmentsBuilder = (
  fragments: MutableList<TextFragment>,
  parameter: Parameter,
  correspondingParameter: Parameter?
) -> Unit

/**
 * Helper class to build presentation for one signature (either old or new).
 *
 * Note that presentation provided by this class is not the final presentation displayed to the user.
 * [SignatureChangePresentationModel.improvePresentation] is used to simplify its appearance:
 * * [Effect.Moved] should not be used, it's generated automatically based on connections between fragments (see [TextFragment.connectionId])
 * * Only connections necessary to display moved elements are shown
 * @param signature signature (either old or new) to build presentation for
 * @param otherSignature other signature (either new or old)
 * @param isOldSignature true if [signature] represents the old signature, or false otherwise
 */
abstract class SignaturePresentationBuilder(
  protected val signature: Signature,
  protected val otherSignature: Signature,
  protected val isOldSignature: Boolean
) {
  protected val fragments: MutableList<TextFragment> = mutableListOf()

  val result: List<TextFragment>
    get() = fragments

  abstract fun buildPresentation()

  protected fun effect(value: String, otherValue: String?): Effect {
    return if (otherValue.isNullOrEmpty()) {
      if (isOldSignature) Effect.Removed else Effect.Added
    }
    else {
      if (otherValue != value) Effect.Modified else Effect.None
    }
  }

  protected fun leaf(value: String, otherValue: String?): Leaf {
    val effect = effect(value, otherValue)
    return Leaf(value, effect)
  }

  @JvmOverloads
  protected fun buildParameterList(prefix: String = "(", suffix: String = ")", parameterBuilder: ParameterFragmentsBuilder) {
    fragments += Leaf(prefix)
    if (signature.parameters.isNotEmpty()) {
      fragments += LineBreak("", indentAfter = true)
    }

    for ((index, parameter) in signature.parameters.withIndex()) {
      if (index > 0) {
        fragments += Leaf(",")
        fragments += LineBreak(" ", indentAfter = true)
      }

      val correspondingParameter = otherSignature.parameterById(parameter.id)
      val connectionId = correspondingParameter?.id

      val effect = if (isOldSignature) {
        if (correspondingParameter == null) Effect.Removed else Effect.None
      }
      else {
        if (correspondingParameter == null) Effect.Added else Effect.None
      }

      fragments += Group(
        mutableListOf<TextFragment>().also {
          parameterBuilder(it, parameter, correspondingParameter)
        },
        effect,
        connectionId
      )
    }

    fragments += LineBreak("", indentAfter = false)
    fragments += Leaf(suffix)
  }
}