// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.codeInsight.CodeInsightSettings
import com.intellij.codeInsight.daemon.impl.HintRenderer
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWhiteSpace
import com.siyeh.ig.psiutils.ExpressionUtils


class MethodChainHintsPass(
  modificationStampHolder: ModificationStampHolder,
  rootElement: PsiElement,
  editor: Editor
) : ElementProcessingHintPass(rootElement, editor, modificationStampHolder) {

  override fun isAvailable(virtualFile: VirtualFile): Boolean = CodeInsightSettings.getInstance().SHOW_METHOD_CHAIN_TYPES_INLINE

  override fun collectElementHints(element: PsiElement, collector: (offset: Int, hint: String) -> Unit) {
    val call = element as? PsiMethodCallExpression ?: return
    if (!isFirstCall(call)) return

    val next = call.nextSibling
    if (!(next is PsiWhiteSpace && next.textContains('\n'))) return
    val chain = collectChain(call)
      .filter {
        val nextSibling = it.nextSibling as? PsiWhiteSpace ?: return@filter false
        nextSibling.textContains('\n')
      }
    if (chain.isEmpty()) return
    val types = chain.mapNotNull { it.type }
    if (types.size != chain.size) return // some type unknown

    val uniqueTypes = mutableSetOf<PsiType>()
    for (i in (0 until types.size - 1)) { // Except last to avoid builder.build() which has obvious type
      uniqueTypes.add(types[i])
    }
    if (uniqueTypes.size < 2) return // to hide hints for builders, where type is obvious
    for ((index, currentCall) in chain.withIndex()) {
      val offset = currentCall.textRange.endOffset
      collector.invoke(offset, types[index].presentableText)
    }
  }

  private fun isFirstCall(call: PsiMethodCallExpression): Boolean {
    val document = myEditor.document
    val callLine = document.getLineNumber(call.argumentList.textOffset)

    val callForQualifier = ExpressionUtils.getCallForQualifier(call)
    if (callForQualifier == null ||
        document.getLineNumber(callForQualifier.argumentList.textOffset) == callLine) return false

    val firstQualifierCall = call.methodExpression.qualifier as? PsiMethodCallExpression
    if (firstQualifierCall != null) {
      if (document.getLineNumber(firstQualifierCall.argumentList.textOffset) != callLine) return false
      var currentQualifierCall: PsiMethodCallExpression = firstQualifierCall
      while (true) {
        val qualifier = currentQualifierCall.methodExpression.qualifier
        if (qualifier == null) return false
        if (qualifier !is PsiMethodCallExpression) return true
        if (document.getLineNumber(qualifier.argumentList.textOffset) != callLine) return false
        currentQualifierCall = qualifier
      }
    }
    return true
  }

  private fun collectChain(call: PsiMethodCallExpression): List<PsiMethodCallExpression> {
    val chain = mutableListOf(call)
    var current = call
    while (true) {
      val nextCall = ExpressionUtils.getCallForQualifier(current)
      if (nextCall == null) break
      chain.add(nextCall)
      current = nextCall
    }
    return chain
  }

  override fun getHintKey(): Key<Boolean> = METHOD_CHAIN_INLAY_KEY
  override fun createRenderer(text: String): HintRenderer = MethodChainHintRenderer(text)

  private class MethodChainHintRenderer(text: String) : HintRenderer(text) {
    override fun getContextMenuGroupId() = "MethodChainHintsContextMenu"
  }

  companion object {
    private val METHOD_CHAIN_INLAY_KEY = Key.create<Boolean>("METHOD_CHAIN_INLAY_KEY")
  }
}