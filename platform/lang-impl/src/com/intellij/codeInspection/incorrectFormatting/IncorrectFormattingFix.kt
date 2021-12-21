// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo.EMPTY
import com.intellij.codeInspection.LocalQuickFix
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.lang.LangBundle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.codeStyle.CodeStyleManager
import org.jetbrains.annotations.Nls


class ReplaceQuickFix(val replacements: List<Pair<TextRange, String>>) : LocalQuickFix {
  override fun getFamilyName() = LangBundle.message("inspection.incorrect.formatting.fix.replace")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    val psiFile = descriptor.psiElement.containingFile ?: return
    WriteCommandAction.runWriteCommandAction(project) {
      PsiDocumentManager
        .getInstance(project)
        .getDocument(psiFile)
        ?.let { document ->
          replacements
            .sortedByDescending { (range, _) -> range.startOffset }
            .forEach { (range, replacement) ->
              document.replaceString(range.startOffset, range.endOffset, replacement)
            }
          PsiDocumentManager.getInstance(project).commitDocument(document)
        }
    }
  }
}


object ReformatQuickFix : LocalQuickFix {
  override fun getFamilyName() = LangBundle.message("inspection.incorrect.formatting.fix.reformat")

  override fun applyFix(project: Project, descriptor: ProblemDescriptor) {
    CodeStyleManager.getInstance(project).reformat(descriptor.psiElement.containingFile, true)
  }
}


abstract class ReconfigureQuickFix(@Nls val family: String, val reconfigure: IncorrectFormattingInspection.() -> Unit) : LocalQuickFix {
  override fun getFamilyName() = family
  override fun startInWriteAction() = false
  override fun generatePreview(project: Project, previewDescriptor: ProblemDescriptor) = EMPTY

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
  { showDetailedWarnings = true }
)

object HideDetailedReportIntention : ReconfigureQuickFix(
  LangBundle.message("inspection.incorrect.formatting.fix.hide.details"),
  { showDetailedWarnings = false }
)
