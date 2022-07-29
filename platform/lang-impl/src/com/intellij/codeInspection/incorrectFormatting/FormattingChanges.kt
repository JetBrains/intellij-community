// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.formatting.service.CoreFormattingService
import com.intellij.formatting.service.FormattingServiceUtil
import com.intellij.lang.ASTNode
import com.intellij.lang.LangBundle
import com.intellij.lang.LanguageFormatting
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategy
import com.intellij.psi.formatter.WhiteSpaceFormattingStrategyFactory
import com.intellij.util.LocalTimeCounter
import org.jetbrains.annotations.Nls

sealed class FormattingChange(val file: PsiFile, val range: TextRange) {
  fun toProblemDescriptor(manager: InspectionManager, isOnTheFly: Boolean): ProblemDescriptor? {
    val fixes = fixes() ?: return null
    return manager.createProblemDescriptor(file, range, message(), ProblemHighlightType.WEAK_WARNING, isOnTheFly, *fixes)
  }

  @Nls
  abstract fun message(): String

  abstract fun fixes(): Array<LocalQuickFix>?
}

class ReplaceChange(file: PsiFile, range: TextRange, val replacement: String) : FormattingChange(file, range) {

  override fun message() = if (range.isEmpty) {
    LangBundle.message("inspection.incorrect.formatting.wrong.whitespace.problem.descriptor.missing.whitespace")
  }
  else {
    LangBundle.message("inspection.incorrect.formatting.wrong.whitespace.problem.descriptor.incorrect.whitespace")
  }

  fun isIndentChange(document: Document): Boolean =
    range.startOffset == 0 || document.text[range.startOffset] == '\n'

  override fun fixes(): Array<LocalQuickFix>? {
    val doc: Document = file.viewProvider.document
    val original = doc.text
    if (original.count { it == '\n' } == replacement.count { it == '\n' }) {
      if (original.substringAfterLast('\n') == replacement.substringAfterLast('\n')) {
        // This change affects only trailing whitespaces in blank lines, skipping
        return null
      }
    }
    return arrayOf(ReplaceQuickFix(listOf(doc.createRangeMarker(range) to replacement)), ReformatQuickFix, HideDetailedReportIntention)
  }
}

class ShiftIndentChange(file: PsiFile, range: TextRange, val node: ASTNode?, val indent: Int) : FormattingChange(file, range) {
  override fun message() = LangBundle.message("inspection.incorrect.formatting.wrong.indent.problem.descriptor")
  override fun fixes() = arrayOf(ReformatQuickFix, HideDetailedReportIntention)
}

object FormattingChanges {
  /**
   * Detects whitespace which is not in agreement with formatting rules for given [file]. Returned [ReplaceChange.range]s always encompass
   * the entirety of the whitespace between tokens for each detected change.
   *
   * @param file
   * @return List of detected changes. `null` if detection could not be performed. Empty list if no changes were detected.
   */
  fun detectIn(file: PsiFile): List<ReplaceChange>? {
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
      thisLogger().warn("${formattingService::class} cannot format an in-memory copy.")
      return null
    }

    //if (formattingService is AbstractDocumentFormattingService) {
    //  AbstractDocumentFormattingService.setDocument(psiCopy, psiCopy.viewProvider.document)
    //  psiCopy.viewProvider.document.putUserData(AsyncDocumentFormattingService.FORMAT_DOCUMENT_SYNCHRONOUSLY, true)
    //}

    CodeStyleManager.getInstance(psiCopy.project).reformat(psiCopy, true)

    val preFormat = fileDoc.text
    val postFormat = copyDoc.text

    return preFormat
      .diffWhitespaceWith(postFormat,
                          WhiteSpaceFormattingStrategyFactory.getStrategy(baseLanguage),
                          ChangeFactory { preStart, preEnd, postStart, postEnd ->
                            ReplaceChange(file, TextRange(preStart, preEnd), postFormat.substring(postStart, postEnd))
                          })
  }

  private fun interface ChangeFactory<T> {
    fun createChange(preStart: Int, preEnd: Int, postStart: Int, postEnd: Int): T
  }

  private fun <T> CharSequence.diffWhitespaceWith(other: CharSequence,
                                          whiteSpaceFormattingStrategy: WhiteSpaceFormattingStrategy,
                                          changeFactory: ChangeFactory<T>): List<T> {
    val changes = mutableListOf<T>()
    val seq = this@diffWhitespaceWith
    var i = 0
    var j = 0
    while (i < seq.length && j < other.length) {
      val iWsEnd = whiteSpaceFormattingStrategy.check(seq, i, seq.length)
      val jWsEnd = whiteSpaceFormattingStrategy.check(other, j, other.length)
      if (iWsEnd > i || jWsEnd > j) {
        val iWsStart = i
        val jWsStart = j
        val iWsLen = iWsEnd - iWsStart
        val jWsLen = jWsEnd - jWsStart

        var changeDetected = false
        if (iWsLen == jWsLen) {
          while (i < iWsEnd) {
            if (seq[i] != other[j]) changeDetected = true
            ++i
            ++j
          }
        }

        if (changeDetected || iWsLen != jWsLen) {
          changes += changeFactory.createChange(iWsStart, iWsEnd, jWsStart, jWsEnd)
        }

        i = iWsEnd
        j = jWsEnd
      }
      else if (seq[i] != other[j]) {
        throw IllegalArgumentException("Non-whitespace change")
      }
      else {
        ++i
        ++j
      }
    }
    return changes
  }
}