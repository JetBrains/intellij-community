// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInsight.daemon.HighlightDisplayKey
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ui.InspectionOptionsPanel
import com.intellij.lang.LangBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.profile.codeInspection.InspectionProjectProfileManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.util.concurrent.atomic.AtomicBoolean


val INSPECTION_KEY = Key.create<IncorrectFormattingInspection>(IncorrectFormattingInspection().shortName)
var notificationShown = AtomicBoolean(false)

class IncorrectFormattingInspection(
  @JvmField var silentMode: Boolean = true,
  @JvmField var reportPerFile: Boolean = false,
  @JvmField var forceForKotlin: Boolean = false
) : LocalInspectionTool() {

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {

    // Skip files we are not able to fix
    if (!file.isWritable) return null
    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null


    // TODO process kotlin files in compatible manner
    //if (isKotlinInspectionEnabled(file.project)) {
    //  return inScopeOf(file, document, manager, isOnTheFly) {
    //    getAllReports(false, reportPerFile)
    //      .takeIf { it.isNotEmpty() }
    //      ?.toTypedArray()
    //  }
    //}

    // Fast check
    if (silentMode && notificationShown.get()) return null

    return inScopeOf(file, document, manager, isOnTheFly) {
      getAllReports(silentMode, reportPerFile)
        .takeIf { it.isNotEmpty() }
        ?.toTypedArray()
    }

  }

  override fun createOptionsPanel() = object : InspectionOptionsPanel(this) {
    init {
      addCheckbox(LangBundle.message("inspection.incorrect.formatting.setting.silent.mode"), "silentMode")
      addCheckbox(LangBundle.message("inspection.incorrect.formatting.setting.report.per.file"), "reportPerFile")
      addCheckbox("forceForKotlin", "forceForKotlin")
    }
  }

  override fun runForWholeFile() = true
  override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WEAK_WARNING
  override fun isEnabledByDefault() = true

}

//val kotlinInspectionKey: HighlightDisplayKey? by lazy { HighlightDisplayKey.findById("Reformat") }
//private fun isKotlinInspectionEnabled(project: Project) =
//  InspectionProjectProfileManager
//    .getInstance(project)
//    .currentProfile
//    .isToolEnabled(kotlinInspectionKey)


//private val incorrectFormattingInspectionShortName: String by lazy { IncorrectFormattingInspection().shortName }

private fun <R> inScopeOf(file: PsiFile, document: Document, manager: InspectionManager, isOnTheFly: Boolean, body: CheckingScope.() -> R) =
  CheckingScope(file, document, manager, isOnTheFly).body()