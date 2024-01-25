// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.ui

import com.intellij.java.JavaBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.settings.JavaLoggerModel
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class ChooseLoggerDialogWrapper(private val availableLoggers: List<String>,
                                selectedLogger: String,
                                project: Project) : DialogWrapper(project, true) {
  var selectedLogger: String = selectedLogger
    private set

  init {
    title = JavaBundle.message("dialog.title.choose.logger")
    init()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        label(JavaBundle.message("label.configurable.logger.type"))
        comboBox(JavaLoggerModel(availableLoggers, selectedLogger)).onChanged {
          selectedLogger = it.item
        }
      }
    }
  }
}