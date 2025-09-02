// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.syntax.element

import com.intellij.openapi.util.text.containsLineBreak
import com.intellij.openapi.util.text.getLineBreakCount
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.SyntaxElementTypeSet
import com.intellij.platform.syntax.element.SyntaxTokenTypes.WHITE_SPACE
import com.intellij.platform.syntax.parser.WhitespacesAndCommentsBinder
import com.intellij.platform.syntax.syntaxElementTypeSetOf
import com.intellij.pom.java.JavaFeature
import com.intellij.pom.java.LanguageLevel

/**
 * @see com.intellij.psi.impl.source.WhiteSpaceAndCommentSetHolder
 */
object WhiteSpaceAndCommentSetHolder {
  private val PRECEDING_COMMENT_BINDER_WITH_MARKDOWN: WhitespacesAndCommentsBinder = PrecedingWhitespacesAndCommentsBinder(false, true)
  private val SPECIAL_PRECEDING_COMMENT_BINDER_WITH_MARKDOWN: WhitespacesAndCommentsBinder = PrecedingWhitespacesAndCommentsBinder(true, true)
  private val PRECEDING_COMMENT_BINDER_WITHOUT_MARKDOWN: WhitespacesAndCommentsBinder = PrecedingWhitespacesAndCommentsBinder(false, false)
  private val SPECIAL_PRECEDING_COMMENT_BINDER_WITHOUT_MARKDOWN: WhitespacesAndCommentsBinder = PrecedingWhitespacesAndCommentsBinder(true, false)
  private val TRAILING_COMMENT_BINDER: WhitespacesAndCommentsBinder = TrailingWhitespacesAndCommentsBinder()

  fun getPrecedingCommentBinder(myLanguageLevel: LanguageLevel): WhitespacesAndCommentsBinder {
    return if (JavaFeature.MARKDOWN_COMMENT.isSufficient(myLanguageLevel))
      PRECEDING_COMMENT_BINDER_WITH_MARKDOWN
    else
      PRECEDING_COMMENT_BINDER_WITHOUT_MARKDOWN
  }

  fun getSpecialPrecedingCommentBinder(myLanguageLevel: LanguageLevel): WhitespacesAndCommentsBinder {
    return if (JavaFeature.MARKDOWN_COMMENT.isSufficient(myLanguageLevel))
      SPECIAL_PRECEDING_COMMENT_BINDER_WITH_MARKDOWN
    else
      SPECIAL_PRECEDING_COMMENT_BINDER_WITHOUT_MARKDOWN
  }

  val trailingCommentBinder: WhitespacesAndCommentsBinder
    get() = TRAILING_COMMENT_BINDER

  val precedingCommentSet: SyntaxElementTypeSet =
    syntaxElementTypeSetOf(JavaSyntaxElementType.MODULE, JavaSyntaxElementType.IMPLICIT_CLASS) +
    SyntaxElementTypes.FULL_MEMBER_BIT_SET

  val trailingCommentSet: SyntaxElementTypeSet =
    syntaxElementTypeSetOf(JavaSyntaxElementType.PACKAGE_STATEMENT) +
    SyntaxElementTypes.IMPORT_STATEMENT_BASE_BIT_SET +
    SyntaxElementTypes.FULL_MEMBER_BIT_SET +
    SyntaxElementTypes.JAVA_STATEMENT_BIT_SET
}

private class PrecedingWhitespacesAndCommentsBinder(
  private val myAfterEmptyImport: Boolean,
  private val mySupportMarkdown: Boolean,
) : WhitespacesAndCommentsBinder {
  override fun getEdgePosition(tokens: List<SyntaxElementType>, atStreamEdge: Boolean, getter: WhitespacesAndCommentsBinder.TokenTextGetter): Int {
    if (tokens.isEmpty()) return 0


    // 1. bind doc comment
    // now there are markdown comments.
    if (mySupportMarkdown) {
      //collect everything
      for (idx in tokens.indices.reversed()) {
        if (tokens[idx] === JavaDocSyntaxElementType.DOC_COMMENT) return idx
      }
    }
    else {
      // To preserve previous orders, let's try to find the first non-markdown comment (and skip markdown comments).
      // If there is no non-markdown, take the first markdown
      for (idx in tokens.indices.reversed()) {
        if (tokens[idx] === JavaDocSyntaxElementType.DOC_COMMENT && !isDocMarkdownComment(idx, getter)) {
          return idx
        }
      }

      for (idx in tokens.indices.reversed()) {
        if (tokens[idx] === JavaDocSyntaxElementType.DOC_COMMENT) return idx
      }
    }

    // 2. bind plain comments
    var result = tokens.size
    for (idx in tokens.indices.reversed()) {
      val tokenType = tokens[idx]
      if (tokenType === WHITE_SPACE) {
        if (getter.get(idx).getLineBreakCount() > 1) {
          break
        }
      }
      else if (SyntaxElementTypes.JAVA_PLAIN_COMMENT_BIT_SET.contains(tokenType)) {
        if (atStreamEdge ||
            (idx == 0 && myAfterEmptyImport) ||
            (idx > 0 && tokens[idx - 1] === WHITE_SPACE) && getter.get(idx - 1).containsLineBreak()) {
          result = idx
        }
      }
      else {
        break
      }
    }

    return result
  }

  private fun isDocMarkdownComment(idx: Int, getter: WhitespacesAndCommentsBinder.TokenTextGetter): Boolean {
    val sequence = getter.get(idx)
    return sequence.startsWith("///")
  }
}

private class TrailingWhitespacesAndCommentsBinder : WhitespacesAndCommentsBinder {
  override fun getEdgePosition(
    tokens: List<SyntaxElementType>,
    atStreamEdge: Boolean,
    getter: WhitespacesAndCommentsBinder.TokenTextGetter,
  ): Int {
    if (tokens.isEmpty()) return 0

    var result = 0
    for (idx in tokens.indices) {
      val tokenType = tokens[idx]
      if (tokenType === WHITE_SPACE) {
        if (getter.get(idx).containsLineBreak()) break
      }
      else if (SyntaxElementTypes.JAVA_PLAIN_COMMENT_BIT_SET.contains(tokenType)) {
        result = idx + 1
      }
      else {
        break
      }
    }

    return result
  }
}
