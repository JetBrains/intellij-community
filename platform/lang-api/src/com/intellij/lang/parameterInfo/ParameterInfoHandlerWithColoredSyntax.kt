// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.parameterInfo

import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithColoredSyntax.ParameterInfoHandlerWithColoredSyntaxData
import com.intellij.lang.parameterInfo.ParameterInfoHandlerWithColoredSyntax.SignatureHtmlPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus.Experimental

/**
 * A specialized [com.intellij.lang.parameterInfo.ParameterInfoHandler], which supports colored syntax in parameter info.
 *
 * Subclasses should implement at least [findParameterOwner], [buildSignaturePresentations] and [parameterListSeparator]
 * methods. To support highlighting of mismatched parameters, subclasses may also implement [getMismatchedParameters].
 * To support highlighting of fully matched parameters, subclasses may also implement [isSignatureFullyMatched].
 *
 * Detection of the current parameter is performed by [getSignatureCurrentParameter] method, which by default uses
 * [ParameterHtmlPresentation.actualArgumentCodeRange]. If the code range is not provided, or if the default behavior is incorrect,
 * subclasses should also override [getSignatureCurrentParameter].
 *
 * To support iteration through parameters using TAB, subclasses should implement [ParameterInfoHandlerWithTabActionSupport] interface.
 *
 * @param ParameterOwner Class of the PsiElement, which is the supported parameter owner at the current caret position.
 * @param SigPresentation Subclasses may use a specialized class, which can hold additional information, especially through a customized
 *   [ParameterHtmlPresentation] implementation.
 *
 * @see [ParameterInfoHandlerWithTabActionSupport]
 */
@Experimental
abstract class ParameterInfoHandlerWithColoredSyntax<ParameterOwner : PsiElement, SigPresentation : SignatureHtmlPresentation> : ParameterInfoHandler<ParameterOwner, ParameterInfoHandlerWithColoredSyntaxData> {

  /**
   * Finds the PsiElement, which is the parameter owner at the current caret position.
   *
   * @return parameter owner, or null if no parameter owner was found at the current caret position.
   */
  protected abstract fun findParameterOwner(file: PsiFile, offset: Int): ParameterOwner?

  /**
   * The core method of the [ParameterInfoHandler]. The returned object should contain a list of parameters and information
   * whether the signature is deprecated. Each of the parameters should have a name and a type with an optional default value.
   * Both strings should be provided with HTML markup. To render colored syntax, methods of
   * [com.intellij.lang.documentation.QuickDocHighlightingHelper] can be used. Each parameter may be matched to the provided
   * argument in the parameter owner arguments list through the optional [ParameterHtmlPresentation.actualArgumentCodeRange]. If the
   * source code range is not provided, [getSignatureCurrentParameter] should be overridden to provide the current parameter index.
   *
   * If base [SignatureHtmlPresentation] and [ParameterHtmlPresentation] interfaces are enough, subclasses may use
   * `SignatureHtmlPresentation()` and `ParameterHtmlPresentation()` methods to create objects of these classes.
   */
  protected abstract fun buildSignaturePresentations(parameterOwner: ParameterOwner): List<SigPresentation>

  /**
   * An HTML string, which will be used as a separator between elements in the parameter info list.
   * For many languages this would be a `,` character. A space between the elements is automatically added.
   */
  protected abstract val parameterListSeparator: String

  /**
   * Implement to support the display of mismatched parameters. Such parameters would appear
   * with a red highlight. The code should return the set of all mismatched parameters, which
   * have a corresponding argument in the parameter owner arguments list, even these which
   * are ahead of the current caret position.
   */
  protected open fun getMismatchedParameters(
    signature: SigPresentation,
    parameterOwner: ParameterOwner,
  ): Set<ParameterHtmlPresentation> = emptySet()

  /**
   * Implement to support the display of fully matched signatures. Such signatures would appear
   * with a green background. The code should return true if the given signature is fully matched
   * with all provided parameter owner arguments. It means that the signature will be used in the runtime.
   * The green highlight will show up only if there is a single signature matched and only if there are multiple
   * signatures/overloads provided.
   */
  protected open fun isSignatureFullyMatched(
    signature: SigPresentation,
    parameterOwner: ParameterOwner,
  ): Boolean = false

  /**
   * If the handler is not able to provide the [ParameterHtmlPresentation.actualArgumentCodeRange] when creating presentation,
   * it should override this method to provide the current parameter index.
   */
  protected open fun getSignatureCurrentParameter(
    signature: SigPresentation,
    parameterOwner: ParameterOwner,
    context: ParameterInfoContext,
  ): Int {
    val text = context.editor.document.text
    val offset = StringUtil.skipWhitespaceForward(text, context.offset)
    return signature.parameters
      .indexOfLast { it.actualArgumentCodeRange != null && offset in it.actualArgumentCodeRange!!.startOffset..StringUtil.skipWhitespaceForward(text, it.actualArgumentCodeRange!!.endOffset) }
  }

  /**
   * A presentation of a call signature in the parameter info list.
   *
   * @property parameters A list of signature parameters with HTML presentation
   * @property deprecated Whether the call signature is deprecated
   */
  interface SignatureHtmlPresentation {
    val parameters: List<ParameterHtmlPresentation>
    val deprecated: Boolean get() = false
  }

  /**
   * A presentation of a call signature parameter in the parameter info list.
   *
   * @property nameAndType A string with HTML markup, which should contain the parameter name and the type.
   * @property actualArgumentCodeRange An optional range of the corresponding argument in the source code. To render colored syntax, methods of
   *  [com.intellij.lang.documentation.QuickDocHighlightingHelper] can be used.
   * @property defaultValue An optional string with HTML markup, which should contain the default value of the parameter if any.
   */
  interface ParameterHtmlPresentation {
    val nameAndType: String
    val defaultValue: String? get() = null
    val actualArgumentCodeRange: TextRange? get() = null
  }

  protected fun SignatureHtmlPresentation(parameters: List<ParameterHtmlPresentation>, deprecated: Boolean = false): SignatureHtmlPresentation =
    object : SignatureHtmlPresentation {
      override val parameters: List<ParameterHtmlPresentation> = parameters
      override val deprecated: Boolean = deprecated
    }

  protected fun ParameterHtmlPresentation(nameAndType: String, actualArgumentCodeRange: TextRange? = null, defaultValue: String? = null): ParameterHtmlPresentation =
    object : ParameterHtmlPresentation {
      override val nameAndType: String get() = nameAndType
      override val defaultValue: String? get() = defaultValue
      override val actualArgumentCodeRange: TextRange? get() = actualArgumentCodeRange
    }

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
        it.mismatchedParameters = getMismatchedParameters(it.signatureHtmlPresentation as SigPresentation, parameterOwner)
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