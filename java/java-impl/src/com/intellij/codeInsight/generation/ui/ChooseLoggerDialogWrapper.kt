// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.generation.ui

import com.intellij.java.JavaBundle
import com.intellij.lang.logging.JvmLogger
import com.intellij.openapi.components.service
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.logging.JvmLoggingConfigurable
import com.intellij.ui.logging.JvmLoggingSettingsStorage
import org.jetbrains.annotations.TestOnly
import javax.swing.JComponent

public class ChooseLoggerDialogWrapper(
  private val project: Project,
  private val availableLoggers: List<JvmLogger>,
  selectedLogger: JvmLogger,
) : DialogWrapper(project, true) {
  public var selectedLogger: JvmLogger = selectedLogger
    private set

  private lateinit var comboBox: Cell<ComboBox<JvmLogger>>
  private val settings = project.service<JvmLoggingSettingsStorage>().state

  init {
    title = JavaBundle.message("dialog.title.choose.logger")
    init()
  }

  @TestOnly
  public fun setComboBoxItem(item: JvmLogger) {
    comboBox.component.item = item
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        label(JavaBundle.message("label.configurable.logger.type"))
        comboBox = comboBox(availableLoggers)
          .onChanged {
            selectedLogger = it.item
          }
          .apply { this.component.item = JvmLogger.getLoggerById(settings.loggerId) }
      }
      row {
        text(JavaBundle.message("link.configurable.logger.generator.display.name")) {
          ShowSettingsUtil.getInstance().showSettingsDialog(project, JvmLoggingConfigurable::class.java)
          val savedLoggerName = settings.loggerId
          comboBox.component.item = JvmLogger.getLoggerById(savedLoggerName)
        }
      }
    }
  }
}