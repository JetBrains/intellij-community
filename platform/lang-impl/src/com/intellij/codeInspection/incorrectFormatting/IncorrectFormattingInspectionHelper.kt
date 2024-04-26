// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.lang.LangBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls
import kotlin.math.abs


internal class IncorrectFormattingInspectionHelper(
  private val formattingChanges: FormattingChanges,
  val file: PsiFile,
  val document: Document,
  val manager: InspectionManager,
  val isOnTheFly: Boolean) {

  fun createAllReports(): Array<ProblemDescriptor>? {
    val mismatches = formattingChanges.mismatches
    if (mismatches.isEmpty()) return null

    val result = arrayListOf<ProblemDescriptor>()

    val (indentMismatches, inLineMismatches) = classifyMismatches(mismatches)
    result += indentMismatchDescriptors(indentMismatches)
    result += inLineMismatchDescriptors(inLineMismatches)

    return result
      .takeIf { it.isNotEmpty() }
      ?.toTypedArray()
  }

  fun createGlobalReport(): ProblemDescriptor =
    manager.createProblemDescriptor(
      file,
      LangBundle.message("inspection.incorrect.formatting.global.problem.descriptor", file.name),
      arrayOf(ReformatQuickFix, ShowDetailedReportIntention),
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      isOnTheFly,
      false
    )

  private fun isIndentMismatch(mismatch: FormattingChanges.WhitespaceMismatch): Boolean =
    mismatch.preFormatRange.startOffset.let {
      it == 0 || document.text[it] == '\n'
    }

  // Distinguish between indent mismatches and others (aka in-line)
  private fun classifyMismatches(mismatches: List<FormattingChanges.WhitespaceMismatch>)
    : Pair<List<FormattingChanges.WhitespaceMismatch>, List<FormattingChanges.WhitespaceMismatch>> {
    val indentMismatch = mismatches.groupBy { isIndentMismatch(it) }

    return Pair(
      indentMismatch[true] ?: emptyList(),
      indentMismatch[false] ?: emptyList()
    )
  }

  // Start line mismatches, "indents", grouped by consequent lines
  private fun indentMismatchDescriptors(indentMismatches: List<FormattingChanges.WhitespaceMismatch>) =
    sequence {
      val currentBlock = arrayListOf<FormattingChanges.WhitespaceMismatch>()
      indentMismatches.forEach { mismatch ->
        currentBlock.lastOrNull()?.let { prev ->
          if (!document.areRangesAdjacent(prev.preFormatRange, mismatch.preFormatRange)) {
            yieldAll(createIndentProblemDescriptors(currentBlock))
            currentBlock.clear()
          }
        }
        currentBlock.add(mismatch)
      }
      yieldAll(createIndentProblemDescriptors(currentBlock))
    }

  private fun createReplacementString(mismatch: FormattingChanges.WhitespaceMismatch): String =
    mismatch.postFormatRange.let {
      formattingChanges.postFormatText.substring(it.startOffset, it.endOffset)
    }

  private fun TextRange.excludeLeadingLinefeed(): TextRange =
    if (!isEmpty && document.text[startOffset] == '\n') TextRange(startOffset + 1, endOffset) else this

  private fun createMessage(mismatch: FormattingChanges.WhitespaceMismatch) =
    if (mismatch.preFormatRange.isEmpty) {
      LangBundle.message("inspection.incorrect.formatting.wrong.whitespace.problem.descriptor.missing.whitespace")
    }
    else {
      LangBundle.message("inspection.incorrect.formatting.wrong.whitespace.problem.descriptor.incorrect.whitespace")
    }

  private fun createIndentProblemDescriptors(mismatches: ArrayList<FormattingChanges.WhitespaceMismatch>) =
    sequence {
      val blockFix = ReplaceQuickFix(mismatches.map { document.createRangeMarker(it.preFormatRange) to createReplacementString(it) })
      mismatches.forEach {
        yield(
          createProblemDescriptor(
            it.preFormatRange.excludeLeadingLinefeed(),
            createMessage(it),
            blockFix, ReformatQuickFix, HideDetailedReportIntention
          )
        )
      }
    }

  // In-line mismatches, grouped by line
  private fun inLineMismatchDescriptors(inLineMismatches: List<FormattingChanges.WhitespaceMismatch>) =
    sequence {
      yieldAll(
        inLineMismatches
          .groupBy { document.getLineNumber(it.preFormatRange.startOffset) }
          .flatMap { (_, mismatches) ->
            val commonFix = ReplaceQuickFix(mismatches.map { document.createRangeMarker(it.preFormatRange) to createReplacementString(it) })
            mismatches.map {
              createProblemDescriptor(
                it.preFormatRange,
                createMessage(it),
                commonFix, ReformatQuickFix, HideDetailedReportIntention
              )
            }
          }
      )
    }

  private fun createProblemDescriptor(range: TextRange, @Nls message: String, vararg fixes: LocalQuickFix): ProblemDescriptor {
    val element = file.findElementAt(range.startOffset)
    val targetElement = element ?: file
    val targetRange = element
                        ?.textRange
                        ?.intersection(range)
                        ?.shiftLeft(element.textRange.startOffset)    // relative range
                      ?: range                                        // original range if no element

    return manager.createProblemDescriptor(
      targetElement,
      targetRange,
      message,
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      isOnTheFly,
      *fixes
    )
  }

}


private fun Document.areRangesAdjacent(first: TextRange, second: TextRange): Boolean {
  val secondEndLineNumber = if (second.endOffset == 0) 0 else getLineNumber(second.endOffset - 1)
  if (abs(getLineNumber(first.startOffset) - secondEndLineNumber) < 2) return true

  val firstEndLineNumber = if (first.endOffset == 0) 0 else getLineNumber(first.endOffset - 1)
  return abs(getLineNumber(second.startOffset) - firstEndLineNumber) < 2
}