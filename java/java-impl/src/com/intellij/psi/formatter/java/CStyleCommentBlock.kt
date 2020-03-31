// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter.java

import com.intellij.formatting.Block
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.formatter.common.AbstractBlock
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.tree.TokenSet

class CStyleCommentBlock(comment: ASTNode, private val indent: Indent?): AbstractBlock(comment, null, null) {
  val spacing: Spacing?
    get() = if (ranges.isEmpty()) Spacing.getReadOnlySpacing() else null

  override fun getSpacing(child1: Block?, child2: Block): Spacing? =
    if (child1 == null && skipBackAndUp(node) == null) Spacing.getReadOnlySpacing()  // a file header comment
    else child2.getSpacing(null, this)

  private fun skipBackAndUp(node: ASTNode): ASTNode? {
    val prev = TreeUtil.skipElementsBack(node.treePrev, TokenSet.WHITE_SPACE)
    return if (prev == null && node.treeParent != null) skipBackAndUp(node.treeParent) else prev
  }

  override fun getIndent(): Indent? = indent

  override fun buildChildren(): List<Block> {
    val ranges = ranges
    if (ranges.isEmpty()) return emptyList()

    val children = ArrayList<Block>(ranges.size)
    val nodeStart = node.startOffset
    for (i in ranges.indices) {
      val indent = if (i == 0) Indent.getNoneIndent() else Indent.getSpaceIndent(1)
      children += TextLineBlock(ranges[i].shiftRight(nodeStart), null, indent, null)
    }
    return children
  }

  override fun isLeaf(): Boolean = ranges.isEmpty()

  private val ranges: List<TextRange> by lazy {
    val text = node.chars
    if (!text.startsWith("/*")) return@lazy emptyList<TextRange>()

    val result = mutableListOf<TextRange>()
    var start = 0
    for (i in 2 until text.length) {
      val c = text[i]
      // looking for a line start (first non-WS character)
      if (start < 0) {
        if (!Character.isWhitespace(c)) {
          if (c != '*') {
            return@lazy emptyList<TextRange>()  // a line doesn't start with '*'
          }
          start = i
        }
      }
      // looking for a line end ('\n' or end of the comment)
      else if (i == text.length - 1) {
        result += TextRange(start, text.length)
      }
      else if (c == '\n') {
        var end = i - 1
        while (Character.isWhitespace(text[end])) --end
        result += TextRange(start, end + 1)
        start = -1
      }
    }
    result
  }
}