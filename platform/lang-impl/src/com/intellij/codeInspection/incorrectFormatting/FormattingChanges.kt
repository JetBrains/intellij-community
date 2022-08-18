// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.service.CoreFormattingService
import com.intellij.formatting.service.FormattingServiceUtil
import com.intellij.lang.LanguageFormatting
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategy
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategyFactory
import com.intellij.util.LocalTimeCounter

data class FormattingChanges(val preFormatText: CharSequence, val postFormatText: CharSequence, val mismatches: List<WhitespaceMismatch>) {
  data class WhitespaceMismatch(val preFormatRange: TextRange, val postFormatRange: TextRange)
}

/**
 * Detects whitespace which is not in agreement with formatting rules for given [file]. Ranges of the returned
 * [FormattingChanges.mismatches] always encompass the entirety of the whitespace between tokens for each detected change in both the
 * original and formatted text.
 *
 * Uses code style settings associated with [file]. To detect changes using different [CodeStyleSettings], use
 * [CodeStyle.doWithTemporarySettings].
 *
 * @param file
 * @return [FormattingChanges] object describing the changes. `null` if detection could not be performed. Empty list if no changes were
 * detected.
 */
fun detectFormattingChanges(file: PsiFile): FormattingChanges? {
  if (!LanguageFormatting.INSTANCE.isAutoFormatAllowed(file)) {
    return null
  }
  val fileDoc = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
  val baseLanguage = file.viewProvider.baseLanguage

  val psiCopy = PsiFileFactory.getInstance(file.project).createFileFromText(
    file.name,
    file.fileType,
    fileDoc.text,
    LocalTimeCounter.currentTime(),
    false,
    true
  )
  // Necessary if we want to apply the same .editorconfig files
  psiCopy.putUserData(PsiFileFactory.ORIGINAL_FILE, file)
  val copyDoc = psiCopy.viewProvider.document!!

  val formattingService = FormattingServiceUtil.findService(file, true, true)
  if (formattingService !is CoreFormattingService) {
    return null
  }
  val copyService = FormattingServiceUtil.findService(psiCopy, true, true)
  if (formattingService != copyService) {
    logger<FormattingChanges>().warn("${formattingService::class} cannot format an in-memory copy.")
    return null
  }

  //if (formattingService is AbstractDocumentFormattingService) {
  //  AbstractDocumentFormattingService.setDocument(psiCopy, psiCopy.viewProvider.document)
  //  psiCopy.viewProvider.document.putUserData(AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY, true)
  //}

  CodeStyleManager.getInstance(psiCopy.project).reformat(psiCopy, true)

  val preFormat = fileDoc.text
  val postFormat = copyDoc.text
  val changes = diffWhitespace(preFormat,
                               postFormat,
                               WhiteSpaceFormattingStrategyFactory.getStrategy(baseLanguage))
  return FormattingChanges(preFormat, postFormat, changes)
}

/**
 * Assumes the following:
 * 1. `\n` is the line separator for both [pre] and [post]. (Note that [WhiteSpaceFormattingStrategy] implementations also assume this.)
 * 2. [pre] and [post] are identical up to whitespace, as identified by [whiteSpaceFormattingStrategy] */
private fun diffWhitespace(pre: CharSequence,
                           post: CharSequence,
                           whiteSpaceFormattingStrategy: WhiteSpaceFormattingStrategy): List<FormattingChanges.WhitespaceMismatch> {
  val mismatches = mutableListOf<FormattingChanges.WhitespaceMismatch>()
  var i = 0
  var j = 0
  while (i < pre.length && j < post.length) {
    val iWsEnd = whiteSpaceFormattingStrategy.check(pre, i, pre.length)
    val jWsEnd = whiteSpaceFormattingStrategy.check(post, j, post.length)
    if (iWsEnd > i || jWsEnd > j) {
      val iWsStart = i
      val jWsStart = j
      val iWsLen = iWsEnd - iWsStart
      val jWsLen = jWsEnd - jWsStart

      var changeDetected = false
      if (iWsLen == jWsLen) {
        while (i < iWsEnd) {
          if (pre[i] != post[j]) changeDetected = true
          ++i
          ++j
        }
      }

      if (changeDetected || iWsLen != jWsLen) {
        mismatches += FormattingChanges.WhitespaceMismatch(TextRange(iWsStart, iWsEnd), TextRange(jWsStart, jWsEnd))
      }

      i = iWsEnd
      j = jWsEnd
    }
    else if (pre[i] != post[j]) {
      throw IllegalArgumentException("Non-whitespace change")
    }
    else {
      ++i
      ++j
    }
  }
  return mismatches
}