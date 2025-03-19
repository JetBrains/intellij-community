// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints.settings

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.codeInsight.hints.ExcludeListDialog
import com.intellij.lang.Language
import com.intellij.ui.components.ActionLink
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.BoxLayout
import javax.swing.JPanel

@ApiStatus.Internal
class ParameterHintsSettingsPanel(val language: Language,
                                  excludeListSupported: Boolean) : JPanel() {

  init {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    if (excludeListSupported) {
      val label = ActionLink(CodeInsightBundle.message("settings.inlay.java.exclude.list")) {
        ExcludeListDialog(language).show()
      }
      label.alignmentX = Component.LEFT_ALIGNMENT
      add(label)
    }
  }
}
