// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

import com.intellij.icons.AllIcons
import com.intellij.java.JavaBundle
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import javax.swing.JPanel

class JavaConfigurablePanel(settings: JavaSettingsStorage.State) {
  var selectedLogger : JavaLoggerInfo = settings.logger
    private set
  private val loggerModel = JavaLoggerModel(JavaLoggerInfo.allLoggers, settings.logger)

  private lateinit var infoRow : Row

  fun getMainPanel(): JPanel {
    return panel {
      row {
        label(JavaBundle.message("label.configurable.logger.type"))
        comboBox(loggerModel).onChanged {
          selectedLogger = it.item
        }
      }
      infoRow = row {
        icon(AllIcons.General.Warning).align(AlignY.TOP).gap(rightGap = RightGap.SMALL)
        text(JavaBundle.message("java.configurable.logger.not.found"))
      }
    }
  }

  fun reset(settings : JavaSettingsStorage.State) {
    loggerModel.selectedItem = settings.logger
  }
}