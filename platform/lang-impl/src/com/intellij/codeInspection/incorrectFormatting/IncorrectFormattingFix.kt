// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.EMPTY
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.LangBundle
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.project.Project
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.annotations.Nls
import java.util.concurrent.atomic.AtomicBoolean


class ReplaceQuickFix(val replacements: List<Pair<RangeMarker, CharSequence>>) : LocalQuickFix {
  override fun getFamilyName() = LangBundle.message("inspection.incorrect.formatting.fix.replace")
  override fun getFileModifierForPreview(target: PsiFile) = ReplaceQuickFix(replacements)

  private val applied = AtomicBoolean(false)

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    if (!applied.compareAndSet(false, true)) return

    descriptor
      .psiElement
      .containingFile
      ?.viewProvider
      ?.document
      ?.let { doc ->
        replacements
          .sortedByDescending { (range, _) -> range.startOffset }
          .forEach { (range, replacement) ->
            if (range.isValid) {
              doc.replaceString(range.startOffset, range.endOffset, replacement)
            }
          }
        PsiDocumentManager.getInstance(project).commitDocument(doc)
      }
  }
}


object ReformatQuickFix : LocalQuickFix {
  override fun getFamilyName() = LangBundle.message("inspection.incorrect.formatting.fix.reformat")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val innerFile = descriptor.psiElement.containingFile
    val file = innerFile.viewProvider.run { getPsi(baseLanguage) }
    CodeStyleManager.getInstance(project).reformatText(file, 0, file.textLength)
  }
}


abstract class ReconfigureQuickFix(@Nls val family: String, val reconfigure: IncorrectFormattingInspection.() -> Unit) : LocalQuickFix {
  override fun getFamilyName() = family
  override fun startInWriteAction() = false
  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor): IntentionPreviewInfo = EMPTY

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val file = descriptor.psiElement.containingFile
    InspectionProjectProfileManager
      .getInstance(project)
      .currentProfile
      .modifyToolSettings(INSPECTION_KEY, file) { inspection ->
        inspection.reconfigure()
      }
  }
}

object ShowDetailedReportIntention : ReconfigureQuickFix(
  LangBundle.message("inspection.incorrect.formatting.fix.show.details"),
  { reportPerFile = false }
)

object HideDetailedReportIntention : ReconfigureQuickFix(
  LangBundle.message("inspection.incorrect.formatting.fix.hide.details"),
  { reportPerFile = true }
)
