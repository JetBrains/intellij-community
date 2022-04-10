// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.incorrectFormatting;

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType
import com.intellij.formatting.virtualFormattingListener
import com.intellij.lang.LangBundle
import com.intellij.lang.LanguageFormatting
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import kotlin.math.abs


class CheckingScope(val file: PsiFile, val document: Document, val manager: InspectionManager, val isOnTheFly: Boolean) {

  fun createAllReports(changes: List<FormattingChange>): Array<ProblemDescriptor>? {
    if (changes.isEmpty()) return null

    // TODO: move to LangBundle.properties
    // inspection.incorrect.formatting.notification.title=New code style inspection
    // inspection.incorrect.formatting.notification.contents=It can help you maintain a consistent code style in your codebase in any language
    // inspection.incorrect.formatting.notification.action.enable=Enable inspection
    // inspection.incorrect.formatting.notification.action.dont.show=Don’t show again

    // TODO: move to reformat action
    //if (silentMode && notificationShown.compareAndSet(false, true)) {
    //  NotificationGroupManager.getInstance()
    //    .getNotificationGroup("Incorrect Formatting")
    //    .createNotification(
    //      LangBundle.message("inspection.incorrect.formatting.notification.title"),
    //      LangBundle.message("inspection.incorrect.formatting.notification.contents"),
    //      NotificationType.INFORMATION
    //    )
    //    .setImportantSuggestion(false)
    //    .setSuggestionType(true)
    //    .addAction(
    //      createSimpleExpiring(LangBundle.message("inspection.incorrect.formatting.notification.action.enable")) {
    //        InspectionProjectProfileManager
    //          .getInstance(file.project)
    //          .currentProfile
    //          .modifyToolSettings(INSPECTION_KEY, file) { inspection ->
    //            inspection.silentMode = false
    //          }
    //      }
    //    )
    //    .addAction(
    //      createSimpleExpiring(LangBundle.message("inspection.incorrect.formatting.notification.action.dont.show")) {
    //        InspectionProjectProfileManager
    //          .getInstance(file.project)
    //          .currentProfile
    //          .modifyToolSettings(INSPECTION_KEY, file) { inspection ->
    //            inspection.suppressNotification = true
    //          }
    //      }
    //    )
    //    .notify(file.project)
    //
    //  return emptyList()
    //}

    val result = arrayListOf<ProblemDescriptor>()


    // CPP-28543: Disable inspection in case of ShiftIndentChanges
    // They interfere with WhitespaceReplaces
    //result += shiftIndentDescriptors(changes)
    if (changes.any { it is ShiftIndentChange }) {
      return null
    }


    val (indentChanges, inLineChanges) = classifyReplaceChanges(changes)
    result += indentChangeDescriptors(indentChanges)
    result += inLineChangeDescriptors(inLineChanges)

    return result
      .takeIf { it.isNotEmpty() }
      ?.toTypedArray()
  }

  // Collect all formatting changes for the file
  fun getChanges(): List<FormattingChange> {
    if (!LanguageFormatting.INSTANCE.isAutoFormatAllowed(file)) {
      return emptyList()
    }

    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return emptyList()
    val changeCollector = ChangeCollectingListener(file, document.text)

    try {
      file.virtualFormattingListener = changeCollector
      CodeStyleManager.getInstance(file.project).reformat(file, true)
    }
    finally {
      file.virtualFormattingListener = null
    }

    return changeCollector.getChanges()
  }

  fun createGlobalReport() =
    manager.createProblemDescriptor(
      file,
      LangBundle.message("inspection.incorrect.formatting.global.problem.descriptor", file.name),
      arrayOf(ReformatQuickFix, ShowDetailedReportIntention),
      ProblemHighlightType.GENERIC_ERROR_OR_WARNING,
      isOnTheFly,
      false
    )


  // Shift indent changes, added as is
  private fun CheckingScope.shiftIndentDescriptors(changes: List<FormattingChange>) =
    changes
      .filterIsInstance<ShiftIndentChange>()
      .mapNotNull { it.toProblemDescriptor(manager, isOnTheFly) }


  // Distinguish between indent changes and others (aka in-line)
  private fun CheckingScope.classifyReplaceChanges(changes: List<FormattingChange>): Pair<List<ReplaceChange>, List<ReplaceChange>> {
    val replaceChange = changes
      .filterIsInstance<ReplaceChange>()
      .groupBy { it.isIndentChange(document) }

    return Pair(
      replaceChange[true] ?: emptyList(),
      replaceChange[false] ?: emptyList()
    )
  }


  // Start line changes, "indents", grouped by consequent lines
  fun indentChangeDescriptors(indentChanges: List<ReplaceChange>) =
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

  fun createIndentProblemDescriptors(changes: ArrayList<ReplaceChange>) =
    sequence<ProblemDescriptor> {
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
  fun inLineChangeDescriptors(inLineChanges: List<ReplaceChange>) =
    sequence {
      yieldAll(
        inLineChanges
          .groupBy { document.getLineNumber(it.range.startOffset) }
          .flatMap { (lineNumber, changes) ->
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

  fun createProblemDescriptor(range: TextRange, message: String, vararg fixes: LocalQuickFix): ProblemDescriptor {
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
  if (originalText.startsWith("\n\r") || originalText.startsWith("\r\n")) {
    return TextRange(startOffset + 2, endOffset)
  }
  if (originalText.startsWith("\n") || originalText.startsWith("\r")) {
    return TextRange(startOffset + 1, endOffset)
  }
  return this
}
