// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.java.refactoring.suggested

import com.intellij.psi.PsiKeyword
import com.intellij.psi.PsiModifier
import com.intellij.refactoring.suggested.SignatureChangePresentationModel.Effect
import com.intellij.refactoring.suggested.SignatureChangePresentationModel.TextFragment
import com.intellij.refactoring.suggested.SignatureChangePresentationModel.TextFragment.*
import com.intellij.refactoring.suggested.SignaturePresentationBuilder
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport.Signature

internal class JavaSignaturePresentationBuilder(
  signature: Signature,
  otherSignature: Signature,
  isOldSignature: Boolean
) : SignaturePresentationBuilder(signature, otherSignature, isOldSignature) {
  private val addedOrRemovedEffect = if (isOldSignature) Effect.Removed else Effect.Added

  override fun buildPresentation() {
    signature.annotations
      .takeIf { it.isNotEmpty() }
      ?.let {
        fragments += Leaf(it, effect(it, otherSignature.annotations))
        fragments += Leaf(" ")
      }

    fun visibilityText(signature: Signature) = signature.visibility?.takeIf { it != PsiModifier.PACKAGE_LOCAL }

    val visibility = visibilityText(signature)
    if (visibility != null) {
      val visibilityEffect = effect(visibility, visibilityText(otherSignature))
      if (visibilityEffect != Effect.None) { // don't show visibility modifier if not changed
        fragments += Leaf(visibility, visibilityEffect)
        fragments += Leaf(" ")
      }
    }

    if (signature.type != null) {
      fragments += leaf(signature.type!!, otherSignature.type)
      fragments += Leaf(" ")
    }

    fragments += leaf(signature.name, otherSignature.name)

    buildParameterList { fragments, parameter, correspondingParameter ->
      parameter.annotations
        .takeIf { it.isNotEmpty() }
        ?.let {
          fragments += leaf(it, correspondingParameter?.annotations ?: it)
          fragments += Leaf(" ")
        }

      fragments += leaf(parameter.type, correspondingParameter?.type ?: parameter.type)

      fragments += Leaf(" ")

      fragments += leaf(parameter.name, correspondingParameter?.name ?: parameter.name)
    }

    val exceptionTypes = signature.exceptionTypes
    if (exceptionTypes.isNotEmpty()) {
      fragments += LineBreak(" ", indentAfter = false)

      val otherExceptionTypes = otherSignature.exceptionTypes.toMutableList<String?>()
      if (otherExceptionTypes.isEmpty()) {
        fragments += Group(
          mutableListOf<TextFragment>().apply {
            addFragmentsForThrows(exceptionTypes) { Effect.None to null }
          },
          addedOrRemovedEffect,
          null
        )
      }
      else {
        fragments.addFragmentsForThrows(exceptionTypes) { index ->
          val exceptionType = exceptionTypes[index]
          val otherIndex = otherExceptionTypes.indexOf(exceptionType)
          if (otherIndex >= 0) {
            otherExceptionTypes[otherIndex] = null // to avoid duplicate connection to the same leaf
            val oldIndex = if (isOldSignature) index else otherIndex
            Effect.None to ExceptionConnectionId(oldIndex)
          }
          else {
            addedOrRemovedEffect to null
          }
        }
      }
    }
  }

  private fun MutableList<TextFragment>.addFragmentsForThrows(
    exceptionTypes: List<String>,
    effectAndConnectionId: (Int) -> Pair<Effect, Any?>
  ) {
    this += Leaf(PsiKeyword.THROWS)
    this += LineBreak(" ", indentAfter = true)

    for ((index, exceptionType) in exceptionTypes.withIndex()) {
      if (index > 0) {
        this += Leaf(",")
        this += LineBreak(" ", indentAfter = true)
      }

      val (effect, connectionId) = effectAndConnectionId(index)
      this += Leaf(exceptionType, effect, connectionId)
    }
  }

  private data class ExceptionConnectionId(val oldIndex: Int)
}