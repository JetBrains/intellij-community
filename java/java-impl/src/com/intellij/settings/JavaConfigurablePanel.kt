// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

import com.intellij.java.JavaBundle
import com.intellij.ui.dsl.builder.panel
import javax.swing.JPanel

class JavaConfigurablePanel(settings: JavaSettingsStorage.State) {
  var selectedLogger : Logger = settings.logger
    private set

  private val loggerModel = JavaLoggerModel(settings.logger)

  fun createPanel(): JPanel {
    return panel {
      row {
        label(JavaBundle.message("label.configurable.logger.type"))
        comboBox(loggerModel).onChanged {
          selectedLogger = it.item
        }
      }
    }
  }

  fun reset(settings : JavaSettingsStorage.State) {
    loggerModel.selectedItem = settings.logger
  }
}