// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:ApiStatus.Internal

package com.intellij.codeInspection.incorrectFormatting

import com.intellij.codeHighlighting.HighlightDisplayLevel
import com.intellij.codeInspection.InspectionManager
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemDescriptor
import com.intellij.codeInspection.options.OptPane
import com.intellij.codeInspection.options.OptPane.checkbox
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.LangBundle
import com.intellij.lang.Language
import com.intellij.lang.injection.InjectedLanguageManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import org.jetbrains.annotations.ApiStatus

val INSPECTION_KEY: Key<IncorrectFormattingInspection> = Key.create(IncorrectFormattingInspection().shortName)

class IncorrectFormattingInspection(
  @JvmField var reportPerFile: Boolean = false,  // generate only one warning per file
  @JvmField var kotlinOnly: Boolean = false  // process kotlin files normally even in silent mode, compatibility
) : LocalInspectionTool() {

  private val isKotlinPlugged: Boolean by lazy { PluginManagerCore.getPlugin(PluginId.getId("org.jetbrains.kotlin")) != null }

  override fun checkFile(file: PsiFile, manager: InspectionManager, isOnTheFly: Boolean): Array<ProblemDescriptor>? {

    // Skip files we are not able to fix
    if (!file.isWritable) return null

    // Skip injections
    val host = InjectedLanguageManager.getInstance(file.project).getInjectionHost(file)
    if (host != null) {
      return null
    }

    // Perform only for main PSI tree
    val baseLanguage: Language = file.viewProvider.baseLanguage
    val mainFile = file.viewProvider.getPsi(baseLanguage)
    if (file != mainFile) {
      return null
    }

    if (isKotlinPlugged && kotlinOnly && file.language.id != "kotlin") {
      return null
    }

    val document = PsiDocumentManager.getInstance(file.project).getDocument(file) ?: return null

    val formattingChanges = detectFormattingChanges(file) ?: return null
    if (formattingChanges.mismatches.isEmpty()) return null

    val helper = IncorrectFormattingInspectionHelper(formattingChanges, file, document, manager, isOnTheFly)
    return if (reportPerFile) {
      arrayOf(helper.createGlobalReport())
    }
    else {
      helper.createAllReports()
    }
  }

  override fun getOptionsPane(): OptPane = OptPane(
    listOf(checkbox("reportPerFile", LangBundle.message("inspection.incorrect.formatting.setting.report.per.file"))) +
    if (isKotlinPlugged) 
      listOf(checkbox("kotlinOnly", LangBundle.message("inspection.incorrect.formatting.setting.kotlin.only"))) 
    else listOf()
  )

  override fun runForWholeFile(): Boolean = true
  override fun getDefaultLevel(): HighlightDisplayLevel = HighlightDisplayLevel.WEAK_WARNING
  override fun isEnabledByDefault(): Boolean = false

}
