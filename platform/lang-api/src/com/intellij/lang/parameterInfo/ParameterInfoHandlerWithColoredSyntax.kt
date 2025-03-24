// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.parameterInfo

import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithColoredSyntax.ParameterInfoHandlerWithColoredSyntaxData
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithColoredSyntax.SignaturePresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

abstract class ParameterInfoHandlerWithColoredSyntax<ParameterOwner : PsiElement, SigPresentation : SignaturePresentation> : ParameterInfoHandler<ParameterOwner, ParameterInfoHandlerWithColoredSyntaxData> {

  final override fun findElementForParameterInfo(context: CreateParameterInfoContext): ParameterOwner? {
    val parameterOwner = findParameterOwner(context.file ?: return null, context.offset) ?: return null

    val listsList = buildSignaturePresentations(parameterOwner).takeIf { it.isNotEmpty() } ?: return null
    context.itemsToShow = listsList
      .map { ParameterInfoHandlerWithColoredSyntaxData(it, parameterOwner.containingFile.modificationStamp, context.editor) }
      .toTypedArray()
    return parameterOwner
  }

  final override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ParameterOwner? {
    return findParameterOwner(context.file ?: return null, context.offset)
  }

  final override fun showParameterInfo(element: ParameterOwner, context: CreateParameterInfoContext) {
    context.showHint(element, element.textOffset, this)
  }

  final override fun updateParameterInfo(parameterOwner: ParameterOwner, context: UpdateParameterInfoContext) {

    if ((context.objectsToView.getOrNull(0) as? ParameterInfoHandlerWithColoredSyntaxData)
        ?.modCount != parameterOwner.containingFile.modificationStamp) {
      val signatures = buildSignaturePresentations(parameterOwner)

      context.objectsToView.forEachIndexed { index, item ->
        val paramList = signatures.getOrNull(index)
        context.setUIComponentEnabled(index, paramList != null)
        if (paramList != null) {
          @Suppress("UNCHECKED_CAST")
          (item as? ParameterInfoHandlerWithColoredSyntaxData)?.let {
            it.signaturePresentation = paramList
            it.modCount = parameterOwner.containingFile.modificationStamp
          }
        }
      }
    }

    context.objectsToView.forEach {
      @Suppress("UNCHECKED_CAST")
      (it as? ParameterInfoHandlerWithColoredSyntaxData)?.let {
        it.mismatchedParameters = getMismatchedParameters(it.signaturePresentation as SigPresentation, parameterOwner, context)
        it.selectedParameter = getSignatureCurrentParameter(it.signaturePresentation as SigPresentation, parameterOwner, context)
        it.isFullyMatched = it.mismatchedParameters.isEmpty() && isSignatureFullyMatched(it.signaturePresentation as SigPresentation, parameterOwner)
      }
    }

    // For compatibility with tests
    context.setCurrentParameter((context.objectsToView.firstOrNull() as? ParameterInfoHandlerWithColoredSyntaxData)?.selectedParameter
                                ?: -1)
    context.highlightedParameter = if (context.objectsToView.size > 1)
      context.objectsToView.singleOrNull { (it as? ParameterInfoHandlerWithColoredSyntaxData)?.isFullyMatched == true }
    else
      null
  }

  protected abstract fun findParameterOwner(file: PsiFile, offset: Int): ParameterOwner?

  protected abstract fun buildSignaturePresentations(parameterOwner: ParameterOwner): List<SigPresentation>

  protected abstract val parameterListSeparator: String

  protected open fun getMismatchedParameters(
    signature: SigPresentation,
    parameterOwner: ParameterOwner,
    context: ParameterInfoContext,
  ): Set<ParameterPresentation> = emptySet()

  protected open fun isSignatureFullyMatched(
    signature: SigPresentation,
    parameterOwner: ParameterOwner,
  ): Boolean = false

  protected open fun getSignatureCurrentParameter(
    signature: SigPresentation,
    parameterOwner: ParameterOwner,
    context: ParameterInfoContext,
  ): Int {
    val text = context.editor.document.text
    val offset = StringUtil.skipWhitespaceForward(text, context.offset)
    return signature.parameters
      .indexOfLast { it.range != null && offset in it.range!!.startOffset..StringUtil.skipWhitespaceForward(text, it.range!!.endOffset) }
  }

  final override fun updateUI(presentation: ParameterInfoHandlerWithColoredSyntaxData, context: ParameterInfoUIContext) {
    context.setupSignaturePresentation(
      presentation.signaturePresentation.parameters.map {
        ParameterInfoUIContext.ParameterPresentation(it.text, it.defaultValue, presentation.mismatchedParameters.contains(it))
      },
      presentation.selectedParameter,
      parameterListSeparator,
      presentation.signaturePresentation.deprecated
    )
  }

  protected fun SignaturePresentation(parameters: List<ParameterPresentation>, deprecated: Boolean = false): SignaturePresentation =
    object : SignaturePresentation {
      override val parameters: List<ParameterPresentation> = parameters
      override val deprecated: Boolean = deprecated
    }

  interface SignaturePresentation {
    val parameters: List<ParameterPresentation>
    val deprecated: Boolean get() = false
  }

  protected fun ParameterPresentation(text: String, range: TextRange? = null, defaultValue: String? = null): ParameterPresentation =
    object : ParameterPresentation {
      override val text: String get() = text
      override val range: TextRange? get() = range
      override val defaultValue: String? get() = defaultValue
    }

  interface ParameterPresentation {
    val text: String
    val range: TextRange? get() = null
    val defaultValue: String? get() = null
  }

  class ParameterInfoHandlerWithColoredSyntaxData internal constructor(
    internal var signaturePresentation: SignaturePresentation,
    internal var modCount: Long,
    internal var editor: Editor,
  ) {
    internal var selectedParameter: Int = -1
    internal var mismatchedParameters: Set<ParameterPresentation> = emptySet()
    internal var isFullyMatched: Boolean = false
  }
}