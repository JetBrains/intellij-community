// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.codeInspection.incorrectFormatting

import com.intellij.application.options.CodeStyle
import com.intellij.formatting.FormatTextRanges
import com.intellij.formatting.service.CoreFormattingService
import com.intellij.formatting.service.FormattingServiceUtil
import com.intellij.lang.LanguageFormatting
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategy
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategyFactory
import com.intellij.psi.impl.CheckUtil
import com.intellij.psi.impl.source.SourceTreeToPsiMap
import com.intellij.psi.impl.source.codeStyle.CodeFormatterFacade
import com.intellij.psi.impl.source.codeStyle.CoreCodeStyleUtil
import com.intellij.psi.impl.source.tree.RecursiveTreeElementWalkingVisitor
import com.intellij.psi.impl.source.tree.TreeElement
import org.jetbrains.annotations.ApiStatus

private val LOG = logger<FormattingChanges>()

@ApiStatus.Internal
data class FormattingChanges(val preFormatText: CharSequence, val postFormatText: CharSequence, val mismatches: List<WhitespaceMismatch>) {
  data class WhitespaceMismatch(val preFormatRange: TextRange, val postFormatRange: TextRange)
}

/**
 * Detects whitespace which is not in agreement with code style for given [file]. Ranges of the returned
 * [FormattingChanges.mismatches] always encompass the entirety of the whitespace between tokens for each detected change in both the
 * original and formatted text.
 *
 * Uses code style settings associated with [file]. To detect changes using different [CodeStyleSettings], use
 * [CodeStyle.runWithLocalSettings].
 *
 * @param file
 * @return [FormattingChanges] object describing the changes. [FormattingChanges.mismatches] will be empty if no changes were detected.
 * `null` if detection could not be performed.
 */
fun detectFormattingChanges(file: PsiFile): FormattingChanges? {
  if (!LanguageFormatting.INSTANCE.isAutoFormatAllowed(file)) {
    return null
  }
  val fileDoc = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
  val baseLanguage = file.viewProvider.baseLanguage

  val psiCopy = PsiFileFactory.getInstance(file.project).createFileFromText(
    file.name,
    file.language,
    fileDoc.text,
    false,
    true,
    false,
    null
  ) ?: return null
  // Necessary if we want to apply the same .editorconfig files
  psiCopy.putUserData(PsiFileFactory.ORIGINAL_FILE, file)
  val copyDoc = psiCopy.viewProvider.document!!

  val formattingService = FormattingServiceUtil.findService(file, true, true)
  if (formattingService !is CoreFormattingService) {
    return null
  }
  val copyService = FormattingServiceUtil.findService(psiCopy, true, true)
  if (formattingService != copyService) {
    LOG.warn("${formattingService::class} cannot format an in-memory copy.")
    return null
  }

  //if (formattingService is AbstractDocumentFormattingService) {
  //  AbstractDocumentFormattingService.setDocument(psiCopy, psiCopy.viewProvider.document)
  //  psiCopy.viewProvider.document.putUserData(AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY, true)
  //}

  try {
    //CodeStyleManager.getInstance(file.project).reformat(psiCopy, true)
    reformat(psiCopy, copyDoc)

    val preFormat = fileDoc.text
    val postFormat = copyDoc.text
    val changes = diffWhitespace(preFormat,
                                 postFormat,
                                 WhiteSpaceFormattingStrategyFactory.getStrategy(baseLanguage))
    return FormattingChanges(preFormat, postFormat, changes)

  }
  catch (pce: ProcessCanceledException) {
    throw pce
  }
  catch (e: NonWhitespaceChangeException) {
    LOG.error("Non-whitespace change: pre-format=%#04x @ %d, post-format=%#04x @ %d, lang=%s, filetype=%s, path=%s"
                .format(e.pre[e.locPre].code, e.locPre,
                        e.post[e.locPost].code, e.locPost,
                        file.language.id, file.fileType.name, file.viewProvider.virtualFile))
  }
  catch (e: Exception) {
    LOG.error(e)
  }
  return null
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
      throw NonWhitespaceChangeException(pre, post, i, j)
    }
    else {
      ++i
      ++j
    }
  }
  return mismatches
}

private data class NonWhitespaceChangeException(val pre: CharSequence,
                                                val post: CharSequence,
                                                val locPre: Int,
                                                val locPost: Int) : Exception()

// see also CodeStyleManagerImpl.reformatText and CoreFormattingService.formatRanges
private fun reformat(file: PsiFile, doc: Document) {
  val ranges = FormatTextRanges(TextRange(0, doc.textLength), true)

  CheckUtil.checkWritable(file)
  if (!SourceTreeToPsiMap.hasTreeElement(file)) return

  val treeElement = SourceTreeToPsiMap.psiElementToTree(file)
  (treeElement as TreeElement).acceptTree(object : RecursiveTreeElementWalkingVisitor() {})

  val infos = CoreCodeStyleUtil.getRangeFormatInfoList(file, ranges)
  val codeFormatter = CodeFormatterFacade(CodeStyle.getSettings(file), file.getLanguage(), true)
  codeFormatter.processText(file, ranges as FormatTextRanges?, false)
  CoreCodeStyleUtil.postProcessRanges(infos) { range: TextRange? -> CoreCodeStyleUtil.postProcessText(file, range!!, true) }
}