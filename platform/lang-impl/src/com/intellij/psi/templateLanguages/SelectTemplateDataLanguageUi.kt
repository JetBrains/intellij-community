// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.templateLanguages

import com.intellij.lang.LangBundle
import com.intellij.lang.Language
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileTypes
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.PopupStep
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
      TemplateDataLanguageChooser.getInstance(project).chooseDataLanguageAndReparseFile(currentTemplateFile, selectedValue)
    }
    return FINAL_CHOICE
  }
}

@Service(Service.Level.PROJECT)
private class TemplateDataLanguageChooser(private val project: Project, private val scope: CoroutineScope) {
  companion object {
    fun getInstance(project: Project): TemplateDataLanguageChooser {
      return project.service<TemplateDataLanguageChooser>()
    }
  }

  fun chooseDataLanguageAndReparseFile(templateFile: VirtualFile, language: Language) {
    scope.launch(Dispatchers.EDT) {
      TemplateDataLanguageMappings.getInstance(project).setMapping(templateFile, language)
    }
  }
}