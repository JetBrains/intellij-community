// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints.codeVision

import com.intellij.codeInsight.codeVision.CodeVisionHost
import com.intellij.codeInsight.codeVision.CodeVisionInitializer
import com.intellij.codeInsight.codeVision.settings.CodeVisionSettings
import com.intellij.codeInsight.hints.InlayHintsPassFactory
import com.intellij.codeInsight.hints.InlayHintsSwitch
import com.intellij.openapi.project.Project

class CodeVisionSwitch : InlayHintsSwitch {
  companion object {
    fun setCodeVisionEnabled(project: Project, value: Boolean) {
      CodeVisionSettings.instance().codeVisionEnabled = value
      InlayHintsPassFactory.forceHintsUpdateOnNextPass()
      CodeVisionInitializer.getInstance(project).getCodeVisionHost().invalidateProviderSignal.fire(CodeVisionHost.LensInvalidateSignal(null))
    }
  }

  override fun isEnabled(project: Project): Boolean {
    return CodeVisionSettings.instance().codeVisionEnabled
  }

  override fun setEnabled(project: Project, value: Boolean) {
    setCodeVisionEnabled(project, value)
  }
}