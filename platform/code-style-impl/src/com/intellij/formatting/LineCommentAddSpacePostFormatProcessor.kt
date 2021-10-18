// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.formatting

import com.intellij.lang.Commenter
import com.intellij.lang.LanguageCommenters
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.psi.*
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor


class LineCommentAddSpacePostFormatProcessor : PostFormatProcessor {

  override fun processElement(source: PsiElement, settings: CodeStyleSettings) = source
    .also { processText(it.containingFile, it.textRange, settings) }

  override fun processText(source: PsiFile, rangeToReformat: TextRange, settings: CodeStyleSettings): TextRange {

    val language = source.language
    if (settings.getCommonSettings(language).LINE_COMMENT_ADD_SPACE) {
      return rangeToReformat  // Option is disabled
    }

    val commenter = LanguageCommenters.INSTANCE.forLanguage(language)
    val commentFinder = SingleLineCommentFinder(commenter)
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


internal class SingleLineCommentFinder(commenter: Commenter) : PsiElementVisitor() {
  val lineCommentPrefixes = commenter.lineCommentPrefixes.map { it.trim() }
  val commentOffsets = arrayListOf<Int>()

  override fun visitFile(file: PsiFile) = file.acceptChildren(this)

  override fun visitElement(element: PsiElement) = element.acceptChildren(this)

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
