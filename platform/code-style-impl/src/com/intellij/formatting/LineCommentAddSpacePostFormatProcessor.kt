// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting

import com.intellij.lang.Commenter
import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor


private const val REGISTRY_KEY_ENABLED = "formatter.line.comment.add.space.enabled"

class LineCommentAddSpacePostFormatProcessor : PostFormatProcessor {

  override fun processElement(source: PsiElement, settings: CodeStyleSettings) = source
    .also { processText(it.containingFile, it.textRange, settings) }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {

    if (!Registry.`is`(REGISTRY_KEY_ENABLED)) {
      return rangeToReformat  // Feature is forcefully disabled via Registry
    }

    val language = source.language
    if (settings.getCommonSettings(language).LINE_COMMENT_ADD_SPACE) {
      return rangeToReformat  // Option is disabled
    }

    val commenter = LanguageCommenters.INSTANCE.forLanguage(language) ?: return rangeToReformat
    val commentFinder = SingleLineCommentFinder(rangeToReformat, commenter)
    source.accept(commentFinder)

    val commentOffsets = commentFinder.commentOffsets
                           .filter { rangeToReformat.contains(it) }
                           .sorted()
                           .takeIf { it.isNotEmpty() }
                         ?: return rangeToReformat  // Nothing useful found

    val documentManager = PsiDocumentManager.getInstance(source.project)
    val document = documentManager.getDocument(source) ?: return rangeToReformat  // Failed to get document

    // Going backwards to protect earlier offsets from modifications in latter ones
    commentOffsets.asReversed().forEach { document.insertString(it, " ") }

    documentManager.commitDocument(document)

    return rangeToReformat.grown(commentOffsets.size)
  }

}


internal class SingleLineCommentFinder(val rangeToReformat: TextRange, commenter: Commenter) : PsiRecursiveElementVisitor() {
  val lineCommentPrefixes = commenter.lineCommentPrefixes.map { it.trim() }
  val commentOffsets = arrayListOf<Int>()

  override fun visitElement(element: PsiElement) {
    if (element.textRange.intersects(rangeToReformat)) {
      super.visitElement(element)
    }
  }

  override fun visitComment(comment: PsiComment) {
    val commentText = comment.text

    val commentPrefixLength = lineCommentPrefixes
                                .find { commentText.startsWith(it) }             // Find the line comment prefix
                                ?.length                                         // Not found -> not a line comment
                                ?.takeUnless { commentText.length == it }        // Empty comment, no need to add a trailing space
                                ?.takeIf { commentText[it].isLetterOrDigit() }   // Insert space only before word-like symbols to keep
                                                                                 // pseugraphics, shebangs and other fancy stuff
                              ?: return

    commentOffsets.add(comment.textRange.startOffset + commentPrefixLength)
  }
}
