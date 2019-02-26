// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethodCallExpression
import com.intellij.psi.PsiType
import com.intellij.psi.PsiWhiteSpace
import com.siyeh.ig.psiutils.ExpressionUtils

class JavaMethodChainHintsProvider: MethodChainHintsProvider {
  override fun getMethodChainHints(element: PsiElement, editor: Editor): List<InlayInfo> {
    val call = element as? PsiMethodCallExpression ?: return emptyList()
    if (!isFirstCall(call, editor)) return emptyList()

    val next = call.nextSibling
    if (!(next is PsiWhiteSpace && next.textContains('\n'))) return emptyList()
    val chain = collectChain(call)
      .filter {
        val nextSibling = it.nextSibling as? PsiWhiteSpace ?: return@filter false
        nextSibling.textContains('\n')
      }
    if (chain.isEmpty()) return emptyList()
    val types = chain.mapNotNull { it.type }
    if (types.size != chain.size) return emptyList() // some type unknown

    val uniqueTypes = mutableSetOf<PsiType>()
    for (i in (0 until types.size - 1)) { // Except last to avoid builder.build() which has obvious type
      uniqueTypes.add(types[i])
    }
    if (uniqueTypes.size < 2) return emptyList() // to hide hints for builders, where type is obvious

    return chain.withIndex().map { (index, currentCall) -> InlayInfo(types[index].presentableText, currentCall.textRange.endOffset) }
  }


  private fun isFirstCall(call: PsiMethodCallExpression, editor: Editor): Boolean {
    val document = editor.document

    val textOffset = call.argumentList.textOffset
    if (document.textLength - 1 < textOffset) return false
    val callLine = document.getLineNumber(textOffset)

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
}