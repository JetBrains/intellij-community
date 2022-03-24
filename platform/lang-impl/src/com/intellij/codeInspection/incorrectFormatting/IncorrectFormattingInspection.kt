// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.ui.InspectionOptionsPanel
import com.intellij.formatting.service.CoreFormattingService
import com.intellij.formatting.service.FormattingServiceUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.LangBundle
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.util.concurrent.atomic.AtomicBoolean


val INSPECTION_KEY = Key.create<IncorrectFormattingInspection>(IncorrectFormattingInspection().shortName)
var notificationShown = AtomicBoolean(false)

class IncorrectFormattingInspection(
  @JvmField var reportPerFile: Boolean = false,  // generate only one warning per file
  @JvmField var kotlinOnly: Boolean = false  // process kotlin files normally even in silent mode, compatibility
) : LocalInspectionTool() {

  val isKotlinPlugged: Boolean by lazy { PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.kotlin")) != null }

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {

    // Skip files we are not able to fix
    if (!file.isWritable) return null

    // Skip injections
    val host = InjectedLanguageManager.getInstance(file.project).getInjectionHost(file)
    if (host != null) {
      return null
    }

    // Doesn't work with external and async formatters since they modify the file
    if (FormattingServiceUtil.findService(file, true, true) !is CoreFormattingService) {
      return null
    }

    // Perform only for main PSI tree
    val baseLanguage: Language = file.getViewProvider().getBaseLanguage()
    val mainFile = file.getViewProvider().getPsi(baseLanguage)
    if (file != mainFile) {
      return null
    }

    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null

    if (isKotlinPlugged && kotlinOnly && file.language.id != "kotlin") {
      return null
    }

    val scope = CheckingScope(file, document, manager, isOnTheFly)

    val changes = scope
      .getChanges()
      .takeIf { it.isNotEmpty() }
      ?: return null

    return if (reportPerFile) {
      arrayOf(scope.createGlobalReport())
    }
    else {
      scope.createAllReports(changes)
    }
  }

  override fun createOptionsPanel() = object : InspectionOptionsPanel(this) {
    init {
      addCheckbox(LangBundle.message("inspection.incorrect.formatting.setting.report.per.file"), "reportPerFile")
      if (isKotlinPlugged) {
        addCheckbox(LangBundle.message("inspection.incorrect.formatting.setting.kotlin.only"), "kotlinOnly")
      }
    }
  }

  override fun runForWholeFile() = true
  override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WEAK_WARNING
  override fun isEnabledByDefault() = false

}
