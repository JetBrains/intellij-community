// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.codeInspection.ui.InspectionOptionsPanel
import com.intellij.formatting.virtualFormattingListener
import com.intellij.lang.LangBundle
import com.intellij.lang.LanguageFormatting
import com.intellij.openapi.editor.Document
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import kotlin.math.abs


val INSPECTION_KEY = Key.create<IncorrectFormattingInspection>(IncorrectFormattingInspection().shortName)

class IncorrectFormattingInspection(
  @JvmField var showDetailedWarnings: Boolean = false
) : LocalInspectionTool() {

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {

    // Skip files we are not able to fix
    if (!file.isWritable) return null

    val virtualFile = file.virtualFile ?: return null
    val fileIndex = ProjectRootManager.getInstance(file.project).fileIndex
    if (!fileIndex.isInSource(virtualFile)) return null

    if (!LanguageFormatting.INSTANCE.isAutoFormatAllowed(file)) {
      return null
    }

    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null
    val changeCollector = ChangeCollectingListener(file, document.text)

    try {
      file.virtualFormattingListener = changeCollector
      CodeStyleManager.getInstance(file.project).reformat(file, true)
    }
    finally {
      file.virtualFormattingListener = null
    }

    val changes = changeCollector.getChanges()
    if (changes.isEmpty()) return null

    if (!showDetailedWarnings) {
      return createGlobalWarning(manager, file, isOnTheFly)
    }


    val result = arrayListOf<ProblemDescriptor>()

    // Shift indent changes, add as is
    changes.filterIsInstance<ShiftIndentChange>()
      .mapNotNull { it.toProblemDescriptor(manager, isOnTheFly) }
      .let { result.addAll(it) }

    val replaceChange = changes
      .filterIsInstance<ReplaceChange>()
      .groupBy { it.range.startOffset == 0 || document.text[it.range.startOffset] == '\n' || document.text[it.range.startOffset] == '\r' }

    val indentChanges = replaceChange[true] ?: emptyList()
    val inLineChanges = replaceChange[false] ?: emptyList()

    // Start line changes, "indents", grouped by consequent lines
    val currentBlock = arrayListOf<ReplaceChange>()
    indentChanges.forEach { change ->
      currentBlock.lastOrNull()?.let { prev ->
        if (!document.areRangesAdjacent(prev.range, change.range)) {
          result.addAll(currentBlock.createIndentProblemDescriptors(file, document, manager, isOnTheFly))
          currentBlock.clear()
        }
      }
      currentBlock.add(change)
    }
    result.addAll(currentBlock.createIndentProblemDescriptors(file, document, manager, isOnTheFly))

    // In line changes, grouped by line
    inLineChanges
      .groupBy { document.getLineNumber(it.range.startOffset) }
      .flatMap { (lineNumber, changes) ->
        val commonFix = ReplaceQuickFix(changes.map { it.range to it.replacement })
        changes.map {
          manager.createProblemDescriptor(file, it.range, it.message(), ProblemHighlightType.WEAK_WARNING, isOnTheFly, commonFix,
                                          ReformatQuickFix, HideDetailedReportIntention)
        }
      }
      .let { result.addAll(it) }

    return result
      .takeIf { it.isNotEmpty() }
      ?.toTypedArray()


  }

  override fun createOptionsPanel() = object : InspectionOptionsPanel(this) {
    init {
      addCheckbox(LangBundle.message("inspection.incorrect.formatting.fix.show.details"), "showDetailedWarnings")
    }
  }

  override fun runForWholeFile() = true
  override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WEAK_WARNING
  override fun isEnabledByDefault() = false

}

private fun ArrayList<ReplaceChange>.createIndentProblemDescriptors(file: PsiFile,
                                                          document: Document,
                                                          manager: InspectionManager,
                                                          isOnTheFly: Boolean): List<ProblemDescriptor> {
  val blockFix = ReplaceQuickFix(map { it.range to it.replacement })
  return map {
    manager.createProblemDescriptor(file, it.range.excludeLeadingLinefeed(document), it.message(), ProblemHighlightType.WEAK_WARNING, isOnTheFly,
                                    blockFix, ReformatQuickFix, HideDetailedReportIntention)
  }
}

private fun createGlobalWarning(manager: InspectionManager, file: PsiFile, isOnTheFly: Boolean) = arrayOf(
  manager.createProblemDescriptor(
    file,
    LangBundle.message("inspection.incorrect.formatting.global.problem.descriptor", file.name),
    arrayOf(ReformatQuickFix, ShowDetailedReportIntention),
    ProblemHighlightType.WEAK_WARNING,
    isOnTheFly,
    false
  )
)

private fun Document.areRangesAdjacent(first: TextRange, second: TextRange): Boolean {
  if (abs(getLineNumber(first.startOffset) - getLineNumber(second.endOffset - 1)) < 2) return true
  if (abs(getLineNumber(second.startOffset) - getLineNumber(first.endOffset - 1)) < 2) return true
  return false
}

private fun TextRange.excludeLeadingLinefeed(document: Document): TextRange {
  val originalText = substring(document.text)
  if (originalText.startsWith("\n\r") || originalText.startsWith("\r\n")) {
    return TextRange(startOffset + 2, endOffset)
  }
  if (originalText.startsWith("\n") || originalText.startsWith("\r")) {
    return TextRange(startOffset + 1, endOffset)
  }
  return this
}
