// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

import com.intellij.icons.AllIcons
import com.intellij.java.JavaBundle
import com.intellij.ui.dsl.builder.*
import javax.swing.JPanel

class JavaConfigurablePanel(private val settings: JavaSettingsStorage.State) {
  private val panel = panel {
    row {
      label(JavaBundle.message("label.configurable.logger.type"))
      comboBox(JavaLoggerModel(JavaLoggerInfo.allLoggers, settings.logger)).bindItem(settings::logger.toNullableProperty())
    }
    row {
      icon(AllIcons.General.Warning).align(AlignY.TOP).gap(rightGap = RightGap.SMALL)
      text(JavaBundle.message("java.configurable.logger.not.found"))
    }.visible(false)
  }

  fun getMainPanel(): JPanel = panel

  fun isModified(): Boolean = panel.isModified()

  fun apply() = panel.apply()

  fun reset() = panel.reset()
}