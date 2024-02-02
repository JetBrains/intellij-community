// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.ui

import com.intellij.java.JavaBundle
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.settings.JavaLoggerModel
import com.intellij.settings.JavaSettingsStorage
import com.intellij.settings.JvmLoggingConfigurable
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.TestOnly
import javax.swing.JComponent

class ChooseLoggerDialogWrapper(
  private val project: Project,
  private val availableLoggers: List<String>,
  selectedLogger: String,
) : DialogWrapper(project, true) {
  var selectedLogger: String = selectedLogger
    private set

  private lateinit var comboBox: Cell<ComboBox<String>>
  private val settings = project.service<JavaSettingsStorage>().state

  init {
    title = JavaBundle.message("dialog.title.choose.logger")
    init()
  }

  @TestOnly
  fun setComboBoxItem(@Nls item: String) {
    comboBox.component.item = item
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        label(JavaBundle.message("label.configurable.logger.type"))
        comboBox = comboBox(JavaLoggerModel(availableLoggers, selectedLogger)).onChanged {
          selectedLogger = it.item
        }
      }
      row {
        text(JavaBundle.message("link.configurable.logger.generator.display.name")) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, JvmLoggingConfigurable::class.java)
          val savedLoggerName = settings.loggerName
          comboBox.component.item = savedLoggerName
        }
      }
    }
  }
}