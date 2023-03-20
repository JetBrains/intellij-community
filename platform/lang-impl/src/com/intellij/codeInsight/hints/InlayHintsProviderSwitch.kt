// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.hints

import com.intellij.openapi.project.Project

class InlayHintsProviderSwitch : InlayHintsSwitch {
  override fun isEnabled(project: Project): Boolean {
    return InlayHintsSettings.instance().hintsEnabledGlobally()
  }

  override fun setEnabled(project: Project, value: Boolean) {
    InlayHintsSettings.instance().setEnabledGlobally(value)
    InlayHintsPassFactory.forceHintsUpdateOnNextPass()
  }
}