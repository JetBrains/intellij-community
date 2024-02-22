// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.templateLanguages

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.lang.LangBundle
import com.intellij.lang.Language
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.swing.Icon

internal class TemplateDataLanguageChooserPopupStep(languages: List<Language>,
                                                    private val currentTemplateFile: VirtualFile,
                                                    private val project: Project)
  : BaseListPopupStep<Language>(LangBundle.message("template.data.language.chooser.intention.text"), languages) {

  override fun isSpeedSearchEnabled(): Boolean {
    return true
  }

  override fun getIconFor(value: Language): Icon? {
    val associatedFileType = value.associatedFileType ?: FileTypes.UNKNOWN
    return associatedFileType.icon
  }

  override fun getTextFor(value: Language): String {
    return value.displayName
  }

  override fun onChosen(selectedValue: Language?, finalChoice: Boolean): PopupStep<*>? {
    if (selectedValue != null) {
      TemplateLanguageCoroutineScopeProvider.getInstance(project).scope.launch {
        TemplateDataLanguageMappings.getInstance(project).setMapping(currentTemplateFile, selectedValue)
        val psiFile = readAction {
          PsiManager.getInstance(project).findFile(currentTemplateFile)
        } ?: return@launch
        DaemonCodeAnalyzer.getInstance(project).restart(psiFile)
      }
    }
    return FINAL_CHOICE
  }
}

@Service(Service.Level.PROJECT)
private class TemplateLanguageCoroutineScopeProvider(val scope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): TemplateLanguageCoroutineScopeProvider {
      return project.service<TemplateLanguageCoroutineScopeProvider>()
    }
  }
}