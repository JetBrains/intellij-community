// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.parameterInfo

import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithColoredSyntax.ParameterInfoHandlerWithColoredSyntaxData
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithColoredSyntax.SignatureHtmlPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile

abstract class ParameterInfoHandlerWithColoredSyntax<ParameterOwner : PsiElement, SigPresentation : SignatureHtmlPresentation> : ParameterInfoHandler<ParameterOwner, ParameterInfoHandlerWithColoredSyntaxData> {

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
            it.signatureHtmlPresentation = paramList
            it.modCount = parameterOwner.containingFile.modificationStamp
          }
        }
      }
    }

    context.objectsToView.forEach {
      @Suppress("UNCHECKED_CAST")
      (it as? ParameterInfoHandlerWithColoredSyntaxData)?.let {
        it.mismatchedParameters = getMismatchedParameters(it.signatureHtmlPresentation as SigPresentation, parameterOwner, context)
        it.selectedParameter = getSignatureCurrentParameter(it.signatureHtmlPresentation as SigPresentation, parameterOwner, context)
        it.isFullyMatched = it.mismatchedParameters.isEmpty() && isSignatureFullyMatched(it.signatureHtmlPresentation as SigPresentation, parameterOwner)
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
  ): Set<ParameterHtmlPresentation> = emptySet()

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
      .indexOfLast { it.sourceCodeRange != null && offset in it.sourceCodeRange!!.startOffset..StringUtil.skipWhitespaceForward(text, it.sourceCodeRange!!.endOffset) }
  }

  final override fun updateUI(presentation: ParameterInfoHandlerWithColoredSyntaxData, context: ParameterInfoUIContext) {
    context.setupSignatureHtmlPresentation(
      presentation.signatureHtmlPresentation.parameters.map {
        ParameterInfoUIContext.ParameterHtmlPresentation(it.nameAndType, it.defaultValue, presentation.mismatchedParameters.contains(it))
      },
      presentation.selectedParameter,
      parameterListSeparator,
      presentation.signatureHtmlPresentation.deprecated
    )
  }

  protected fun SignatureHtmlPresentation(parameters: List<ParameterHtmlPresentation>, deprecated: Boolean = false): SignatureHtmlPresentation =
    object : SignatureHtmlPresentation {
      override val parameters: List<ParameterHtmlPresentation> = parameters
      override val deprecated: Boolean = deprecated
    }

  interface SignatureHtmlPresentation {
    val parameters: List<ParameterHtmlPresentation>
    val deprecated: Boolean get() = false
  }

  protected fun ParameterHtmlPresentation(nameAndType: String, sourceCodeRange: TextRange? = null, defaultValue: String? = null): ParameterHtmlPresentation =
    object : ParameterHtmlPresentation {
      override val nameAndType: String get() = nameAndType
      override val sourceCodeRange: TextRange? get() = sourceCodeRange
      override val defaultValue: String? get() = defaultValue
    }

  interface ParameterHtmlPresentation {
    val nameAndType: String
    val sourceCodeRange: TextRange? get() = null
    val defaultValue: String? get() = null
  }

  class ParameterInfoHandlerWithColoredSyntaxData internal constructor(
    internal var signatureHtmlPresentation: SignatureHtmlPresentation,
    internal var modCount: Long,
    internal var editor: Editor,
  ) {
    internal var selectedParameter: Int = -1
    internal var mismatchedParameters: Set<ParameterHtmlPresentation> = emptySet()
    internal var isFullyMatched: Boolean = false
  }
}