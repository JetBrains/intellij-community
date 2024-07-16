// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.hints.InlayHintsSwitch
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

internal class CodeVisionSwitch : InlayHintsSwitch {

  override fun isEnabled(project: Project): Boolean {
    return CodeVisionSettings.getInstance().codeVisionEnabled
  }

  override fun setEnabled(project: Project, value: Boolean) {
    setCodeVisionEnabled(project, value)
  }

  private fun setCodeVisionEnabled(project: Project, value: Boolean) {
    CodeVisionSettings.getInstance().codeVisionEnabled = value
    ModificationStampUtil.clearModificationStamp()
    project.service<CodeVisionHost>().invalidateProviderSignal.fire(CodeVisionHost.LensInvalidateSignal(null))
  }
}