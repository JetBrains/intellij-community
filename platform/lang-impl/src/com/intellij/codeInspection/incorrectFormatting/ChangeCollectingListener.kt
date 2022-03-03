// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ProblemHighlightType.WARNING
import com.intellij.lang.ASTNode
import com.intellij.lang.LangBundle
import com.intellij.lang.VirtualFormattingListener
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.Nls


class ChangeCollectingListener(val file: PsiFile, val originalText: String) : VirtualFormattingListener {
  private val changes = arrayListOf<FormattingChange>()

  fun getChanges(): List<FormattingChange> = changes

  override fun shiftIndentInsideRange(node: ASTNode?, range: TextRange, indent: Int) {
    changes.add(ShiftIndentChange(file, range, node, indent))
  }

  override fun replaceWhiteSpace(textRange: TextRange, whiteSpace: String) {
    changes.add(ReplaceChange(file, textRange, whiteSpace))
  }

}

sealed class FormattingChange(val file: PsiFile, val range: TextRange) {
  fun toProblemDescriptor(manager: InspectionManager, isOnTheFly: Boolean): ProblemDescriptor? {
    val fixes = fixes() ?: return null
    return manager.createProblemDescriptor(file, range, message(), WARNING, isOnTheFly, *fixes)
  }

  @Nls
  abstract fun message(): String

  abstract fun fixes(): Array<LocalQuickFix>?

  open fun split(): List<FormattingChange> = listOf(this)
}

class ReplaceChange(file: PsiFile, range: TextRange, val replacement: String) : FormattingChange(file, range) {

  override fun message() = if (range.isEmpty) {
    LangBundle.message("inspection.incorrect.formatting.wrong.whitespace.problem.descriptor.missing.whitespace")
  }
  else {
    LangBundle.message("inspection.incorrect.formatting.wrong.whitespace.problem.descriptor.incorrect.whitespace")
  }

  override fun fixes(): Array<LocalQuickFix>? {
    val original = range.substring(file.text)
    if (original.count { it == '\n' } == replacement.count { it == '\n' }) {
      if (original.substringAfterLast('\n') == replacement.substringAfterLast('\n')) {
        // This change affects only trailing whitespaces in blank lines, skipping
        return null
      }
    }
    return arrayOf(ReplaceQuickFix(listOf(range to replacement)), ReformatQuickFix, HideDetailedReportIntention)
  }

}

class ShiftIndentChange(file: PsiFile, range: TextRange, val node: ASTNode?, val indent: Int) : FormattingChange(file, range) {
  override fun message() = LangBundle.message("inspection.incorrect.formatting.wrong.indent.problem.descriptor")
  override fun fixes() = arrayOf(ReformatQuickFix, HideDetailedReportIntention)
}
