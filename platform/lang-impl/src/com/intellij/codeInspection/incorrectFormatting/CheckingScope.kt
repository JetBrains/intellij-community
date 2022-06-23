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


class CheckingScope(val file: PsiFile, val document: Document, val manager: InspectionManager, val isOnTheFly: Boolean) {

  fun createAllReports(changes: List<FormattingChange>): Array<ProblemDescriptor>? {
    if (changes.isEmpty()) return null

    val result = arrayListOf<ProblemDescriptor>()

    val (indentChanges, inLineChanges) = classifyReplaceChanges(changes)
    result += indentChangeDescriptors(indentChanges)
    result += inLineChangeDescriptors(inLineChanges)

    return result
      .takeIf { it.isNotEmpty() }
      ?.toTypedArray()
  }

  // Collect all formatting changes for the file
  fun getChanges(): List<FormattingChange> = FormattingChanges.detectIn(file) ?: emptyList()

  fun createGlobalReport() =
    manager.createProblemDescriptor(
      file,
      LangBundle.message("inspection.incorrect.formatting.global.problem.descriptor", file.name),
      arrayOf(ReformatQuickFix, ShowDetailedReportIntention),
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      isOnTheFly,
      false
    )


  // Distinguish between indent changes and others (aka in-line)
  private fun classifyReplaceChanges(changes: List<FormattingChange>): Pair<List<ReplaceChange>, List<ReplaceChange>> {
    val replaceChange = changes
      .filterIsInstance<ReplaceChange>()
      .groupBy { it.isIndentChange(document) }

    return Pair(
      replaceChange[true] ?: emptyList(),
      replaceChange[false] ?: emptyList()
    )
  }


  // Start line changes, "indents", grouped by consequent lines
  private fun indentChangeDescriptors(indentChanges: List<ReplaceChange>) =
    sequence {
      val currentBlock = arrayListOf<ReplaceChange>()
      indentChanges.forEach { change ->
        currentBlock.lastOrNull()?.let { prev ->
          if (!document.areRangesAdjacent(prev.range, change.range)) {
            yieldAll(createIndentProblemDescriptors(currentBlock))
            currentBlock.clear()
          }
        }
        currentBlock.add(change)
      }
      yieldAll(createIndentProblemDescriptors(currentBlock))
    }

  private fun createIndentProblemDescriptors(changes: ArrayList<ReplaceChange>) =
    sequence {
      val blockFix = ReplaceQuickFix(changes.map { document.createRangeMarker(it.range) to it.replacement })
      changes.forEach {
        yield(
          createProblemDescriptor(
            it.range.excludeLeadingLinefeed(document),
            it.message(),
            blockFix, ReformatQuickFix, HideDetailedReportIntention
          )
        )
      }
    }

  // In-line changes, grouped by line
  private fun inLineChangeDescriptors(inLineChanges: List<ReplaceChange>) =
    sequence {
      yieldAll(
        inLineChanges
          .groupBy { document.getLineNumber(it.range.startOffset) }
          .flatMap { (_, changes) ->
            val commonFix = ReplaceQuickFix(changes.map { document.createRangeMarker(it.range) to it.replacement })
            changes.map {
              createProblemDescriptor(
                it.range,
                it.message(),
                commonFix, ReformatQuickFix, HideDetailedReportIntention
              )
            }
          }
      )
    }

  fun createProblemDescriptor(range: TextRange, @Nls message: String, vararg fixes: LocalQuickFix): ProblemDescriptor {
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
  if (abs(getLineNumber(first.startOffset) - getLineNumber(second.endOffset - 1)) < 2) return true
  if (abs(getLineNumber(second.startOffset) - getLineNumber(first.endOffset - 1)) < 2) return true
  return false
}

private fun TextRange.excludeLeadingLinefeed(document: Document): TextRange {
  val originalText = substring(document.text)
  return if (originalText.startsWith("\n")) TextRange(startOffset + 1, endOffset) else this
}
