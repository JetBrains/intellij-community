// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.suggested

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.openapi.options.advanced.AdvancedSettingsChangeListener
import com.intellij.openapi.project.ProjectManager

private const val ADV_SETTING_NAME = "promote.suggested.refactoring.in.editor"

internal fun isSuggestedRefactoringEditorHintEnabled(): Boolean {
  return AdvancedSettings.getBoolean(ADV_SETTING_NAME)
}

private class SuggestedRefactoringEditorHintAdvSettingListener: AdvancedSettingsChangeListener {
  override fun advancedSettingChanged(id: String, oldValue: Any, newValue: Any) {
    if (ADV_SETTING_NAME == id) {
      if (newValue == true) {
        ProjectManager.getInstance().openProjects.forEach {
          DaemonCodeAnalyzer.getInstance(it).restart()
        }
      }
      else {
        ProjectManager.getInstance().openProjects.asSequence().forEach {
          SuggestedRefactoringProviderImpl.getInstance(it).suppressForCurrentDeclaration()
        }
      }
    }
  }
}