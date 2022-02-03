// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting

import com.intellij.lang.ASTNode
import com.intellij.lang.VirtualFormattingListener
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.formatter.FormattingDocumentModelImpl


private val formattingListenerKey = Key.create<VirtualFormattingListener?>("VIRTUAL_FORMATTING_CHANGE_LISTENER")

var PsiElement.virtualFormattingListener: VirtualFormattingListener?
  get() = getUserData(formattingListenerKey)
  set(value) = putUserData(formattingListenerKey, value)


class VirtualFormattingModelBuilder(val underlyingBuilder: FormattingModelBuilder,
                                    val file: PsiFile,
                                    val listener: VirtualFormattingListener) : FormattingModelBuilder {

  private fun FormattingModel.wrap(): FormattingModel =
    VirtualFormattingModel(file, rootBlock, listener)

  override fun createModel(formattingContext: FormattingContext) =
    underlyingBuilder.createModel(formattingContext).wrap()

  override fun createModel(element: PsiElement?, settings: CodeStyleSettings?) =
    underlyingBuilder.createModel(element, settings).wrap()

  override fun createModel(element: PsiElement, settings: CodeStyleSettings, mode: FormattingMode) =
    underlyingBuilder.createModel(element, settings, mode).wrap()

  override fun createModel(element: PsiElement, range: TextRange, settings: CodeStyleSettings, mode: FormattingMode) =
    underlyingBuilder.createModel(element, range, settings, mode).wrap()

}


private class VirtualFormattingModel(
  file: PsiFile,
  private val rootBlock: Block,
  private val listener: VirtualFormattingListener) : FormattingModel {

  private val dummyModel = FormattingDocumentModelImpl(DocumentImpl(file.viewProvider.contents, true), file)

  override fun commitChanges() = Unit  // do nothing
  override fun getRootBlock() = rootBlock
  override fun getDocumentModel() = dummyModel

  override fun shiftIndentInsideRange(node: ASTNode?, range: TextRange, indent: Int): TextRange {
    listener.shiftIndentInsideRange(node, range, indent)
    return range
  }

  override fun replaceWhiteSpace(textRange: TextRange, whiteSpace: String): TextRange {
    listener.replaceWhiteSpace(textRange, whiteSpace)
    return textRange
  }
}

fun isEligibleForVirtualFormatting(element: PsiElement): Boolean {
  return element.virtualFormattingListener != null
}

fun wrapForVirtualFormatting(element: PsiElement, builder: FormattingModelBuilder?): FormattingModelBuilder? {
  builder ?: return null
  val listener = element.virtualFormattingListener ?: return builder
  val file = element.containingFile ?: return builder
  return VirtualFormattingModelBuilder(builder, file, listener)
}
