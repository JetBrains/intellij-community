// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInspection.incorrectFormatting

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.psi.codeStyle.CodeStyleSettingsManager

@Service
class IncorrectFormattingInspectionCodeStyleSettingsListenerService(val project: Project) : Disposable {
  private val listener = IncorrectFormattingInspectionCodeStyleSettingsListener(project)
  init {
    registerListener()
  }

  private fun registerListener() {
    CodeStyleSettingsManager.getInstance(project).addListener(listener)
  }

  override fun dispose() {
    CodeStyleSettingsManager.removeListener(project, listener)
  }
}