// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.jsp

import com.intellij.lang.ASTNode
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.psi.impl.source.tree.TreeCopyHandler
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.jsp.JspxLanguage
import com.intellij.psi.templateLanguages.OuterLanguageElement

private val ALREADY_ESCAPED = Key.create<Boolean>("ALREADY_ESCAPED")
private val ESCAPEMENT_ENGAGED = Key.create<Boolean>("ESCAPEMENT_ENGAGED")

internal class JspxTreeCopyHandler : TreeCopyHandler {
  override fun decodeInformation(element: TreeElement, decodingState: MutableMap<Any, Any>): TreeElement? {
    if (conversionEngaged(element, decodingState) && element is LeafElement && element !is OuterLanguageElement && !isInCData(element)) {
      val original = element.text
      val escaped = StringUtil.escapeXmlEntities(original)
      if (original != escaped && element.getCopyableUserData(ALREADY_ESCAPED) == null) {
        val copy = element.replaceWithText(escaped)
        copy.putCopyableUserData(ALREADY_ESCAPED, true)
        return copy
      }
    }
    return null
  }

  override fun encodeInformation(element: TreeElement, original: ASTNode, encodingState: MutableMap<Any, Any>) {
    if (conversionEngaged(original, encodingState) && original is LeafElement &&
        original !is OuterLanguageElement && !isInCData(original)) {
      val originalText = element.text
      val unescapedText = StringUtil.unescapeXmlEntities(originalText)
      if (originalText != unescapedText) {
        val replaced = (element as LeafElement).rawReplaceWithText(unescapedText)
        element.putCopyableUserData(ALREADY_ESCAPED, null)
        replaced.putCopyableUserData(ALREADY_ESCAPED, null)
      }
    }
  }

  private fun conversionEngaged(original: ASTNode, state: MutableMap<Any, Any>): Boolean =
    state.getOrPut(ESCAPEMENT_ENGAGED) { conversionMayApply(original) } as Boolean

  private fun conversionMayApply(element: ASTNode): Boolean {
    val psi = element.psi ?: return false
    if (!psi.isValid) return false
    val file = psi.containingFile
    val baseLanguage = file.viewProvider.baseLanguage
    return baseLanguage is JspxLanguage && file.language != baseLanguage
  }

  private fun isInCData(element: ASTNode): Boolean {
    var leaf: ASTNode? = element
    while (leaf != null) {
      if (leaf is OuterLanguageElement) return leaf.text.contains("<![CDATA[")
      leaf = TreeUtil.prevLeaf(leaf)
    }
    return false
  }
}
