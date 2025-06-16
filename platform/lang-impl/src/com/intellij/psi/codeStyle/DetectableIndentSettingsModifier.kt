// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle

import com.intellij.application.options.CodeStyle
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.modifier.CodeStyleSettingsModifier
import com.intellij.psi.codeStyle.modifier.CodeStyleStatusBarUIContributor
import com.intellij.psi.codeStyle.modifier.TransientCodeStyleSettings
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.function.Consumer

/**
 * This implementation is used in Setting UI as the yellow warning about detectable indents in action.
 */
@ApiStatus.Experimental
class DetectableIndentSettingsModifier : CodeStyleSettingsModifier {
  override fun modifySettings(settings: TransientCodeStyleSettings, file: PsiFile): Boolean = false

  override fun getDisablingFunction(project: Project): Consumer<CodeStyleSettings?> = Consumer { settings: CodeStyleSettings ->
    settings.AUTODETECT_INDENTS = false
  }

  override fun modifySettingsAndUiCustomization(settings: TransientCodeStyleSettings, file: PsiFile): Boolean {
    if (settings.AUTODETECT_INDENTS && DetectableIndentOptionsProvider.getInstance()?.isApplicableForFile(file.virtualFile) == true) {
      settings.setModifier(this)
    }
    return modifySettings(settings, file)
  }

  override fun getName(): @Nls(capitalization = Nls.Capitalization.Title) String = ApplicationBundle.message("code.style.indent.detector.title")

  override fun getStatusBarUiContributor(transientSettings: TransientCodeStyleSettings): CodeStyleStatusBarUIContributor? = null

  override fun mayOverrideSettingsOf(project: Project): Boolean = CodeStyle.getSettings(project).AUTODETECT_INDENTS
}
