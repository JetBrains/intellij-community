// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.logging

import com.intellij.icons.AllIcons
import com.intellij.java.JavaBundle
import com.intellij.lang.logging.JvmLogger
import com.intellij.lang.logging.JvmLoggerFieldDelegate
import com.intellij.lang.logging.UnspecifiedLogger
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.util.concurrency.AppExecutorUtil
import javax.swing.JComponent


class JvmLoggingConfigurable(private val project: Project) : SearchableConfigurable, NoScroll {
  private lateinit var warningRow: Row
  private lateinit var panel: DialogPanel
  private val settings = project.service<JvmLoggingSettingsStorage>().state

  override fun getDisplayName(): String = JavaBundle.message("jvm.logging.configurable.display.name")

  override fun getId(): String = JavaBundle.message("jvm.logging.configurable.id")

  override fun createComponent(): JComponent {
    val loggers = JvmLogger.getAllLoggers(settings.loggerId == UnspecifiedLogger.UNSPECIFIED_LOGGER_ID)
    panel = panel {
      group(JavaBundle.message("jvm.logging.configurable.java.group.display.name")) {
        row {
          label(JavaBundle.message("label.configurable.logger.preferred.name"))
          textField()
            .columns(COLUMNS_SHORT)
            .bindText(settings::loggerName.toNonNullableProperty(JvmLoggerFieldDelegate.LOGGER_IDENTIFIER))
            .align(AlignX.FILL)
        }
        row {
          label(JavaBundle.message("label.configurable.logger.type"))
          comboBox(loggers)
            .bindItem({ JvmLogger.getLoggerById(settings.loggerId) },
                      { settings.loggerId = it?.id })
            .onChanged { updateWarningRow(it.item) }
            .align(AlignX.FILL)
        }
        warningRow = row {
          icon(AllIcons.General.Warning).align(AlignY.TOP).gap(rightGap = RightGap.SMALL)
          text(JavaBundle.message("java.configurable.logger.not.found"))
        }.visible(false)
      }
    }
    updateWarningRow(JvmLogger.getLoggerById(settings.loggerId))
    return panel
  }

  private fun updateWarningRow(logger: JvmLogger?) {
    ReadAction.nonBlocking<Boolean> { logger?.isAvailable(project) == false && logger !is UnspecifiedLogger }
      .finishOnUiThread(ModalityState.any()) { isVisible -> warningRow.visible(isVisible) }
      .submit(AppExecutorUtil.getAppExecutorService())
  }

  override fun isModified(): Boolean = panel.isModified()

  override fun reset() = panel.reset()

  override fun apply() = panel.apply()
}
