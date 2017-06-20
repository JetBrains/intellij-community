/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.formatting.blocks

import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.TokenType
import com.intellij.psi.formatter.common.AbstractBlock


fun ASTNode.prev(): ASTNode? {
  var prev = treePrev
  while (prev != null && prev.elementType == TokenType.WHITE_SPACE) {
    prev = prev.treePrev
  }
  if (prev != null) return prev
  return if (treeParent != null) treeParent.prev() else null
}


class CStyleCommentBlock(comment: ASTNode, private val indent: Indent?): AbstractBlock(comment, null, null) {

  private val lines by lazy { lineBlocks() }
  val isCommentFormattable by lazy {
    lines.drop(1).all { it.text.startsWith("*") }
  }

  val spacing: Spacing?
    get() = if (isCommentFormattable) null else Spacing.getReadOnlySpacing()

  override fun getSpacing(child1: Block?, child2: Block): Spacing? {
    val isLicenseComment = child1 == null && node.prev() == null
    if (isLicenseComment) {
      return Spacing.getReadOnlySpacing()
    }

    return child2.getSpacing(null, this)
  }

  override fun getIndent() = indent

  override fun buildChildren(): List<Block> {
    if (!isCommentFormattable) return emptyList()

    return lines.map {
      val text = it.text
      val indent = when {
        !isCommentFormattable -> null
        text.startsWith("/*") -> Indent.getNoneIndent()
        else -> Indent.getSpaceIndent(1)
      }
      TextLineBlock(text, it.textRange, null, indent, null)
    }
  }


  private fun lineBlocks(): List<LineInfo> {
    return node.text
        .mapIndexed { index, char -> index to char }
        .split { it.second == '\n' }
        .mapNotNull {
          val block = it.dropWhile { Character.isWhitespace(it.second) }
          if (block.isEmpty()) return@mapNotNull null

          val text = block.map { it.second }.joinToString("").trimEnd()

          val startOffset = node.startOffset + block.first().first
          val range = TextRange(startOffset, startOffset + text.length)

          LineInfo(text, range)
        }
  }

  override fun isLeaf() = !isCommentFormattable

}


private class LineInfo(val text: String, val textRange: TextRange)


class TextLineBlock(
    val text: String,
    private val textRange: TextRange,
    private val alignment: Alignment?,
    private val indent: Indent?,
    val spacing: Spacing?
) : Block {

  override fun getTextRange(): TextRange {
    return textRange
  }

  override fun getSubBlocks(): List<Block> = emptyList()

  override fun getWrap() = null

  override fun getIndent() = indent

  override fun getAlignment() = alignment

  override fun getSpacing(child1: Block?, child2: Block) = spacing

  override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
    throw UnsupportedOperationException("Should not be called")
  }

  override fun isIncomplete() = false

  override fun isLeaf() = true

  override fun toString(): String {
    return "TextLineBlock(text='$text', textRange=$textRange)"
  }

}


fun <T> List<T>.split(predicate: (T) -> Boolean): List<List<T>> {
  if (indices.isEmpty()) return listOf()
  val result = mutableListOf<List<T>>()

  val current = mutableListOf<T>()
  for (e in this) {
    if (predicate(e)) {
      result.add(current.toList())
      current.clear()
    }
    else {
      current.add(e)
    }
  }

  if (current.isNotEmpty()) {
    result.add(current)
  }

  return result
}
