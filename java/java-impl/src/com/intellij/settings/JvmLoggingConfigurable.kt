// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

import com.intellij.icons.AllIcons
import com.intellij.java.JavaBundle
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.logging.JvmLogger
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
  private val boundedExecutor = AppExecutorUtil.getAppExecutorService()
  private val settings = project.service<JavaSettingsStorage>().state

  override fun getDisplayName(): String = JavaBundle.message("jvm.logging.configurable.display.name")

  override fun getId(): String = JavaBundle.message("jvm.logging.configurable.display.name")

  override fun createComponent(): JComponent {
    panel = panel {
      group(JavaBundle.message("jvm.logging.configurable.java.group.display.name")) {
        row {
          label(JavaBundle.message("label.configurable.logger.type"))
          comboBox(JavaLoggerModel(JvmLogger.getAllLoggersNames(), settings.logger)).bindItem(
            settings::logger.toNullableProperty()).onChanged {
            updateWarningRow(it.item)
          }
        }
        warningRow = row {
          icon(AllIcons.General.Warning).align(AlignY.TOP).gap(rightGap = RightGap.SMALL)
          text(JavaBundle.message("java.configurable.logger.not.found"))
        }.visible(false)
      }
    }
    updateWarningRow(settings.logger)
    return panel
  }

  private fun updateWarningRow(loggerDisplayName: String?) {
    ReadAction.nonBlocking<Boolean> {
      val logger = JvmLogger.EP_NAME.extensionList.find { it.toString() == loggerDisplayName } ?: return@nonBlocking false
      !JavaLibraryUtil.hasLibraryClass(project, logger.loggerName)
    }.finishOnUiThread(ModalityState.any()) { isVisible ->
      warningRow.visible(isVisible)
    }.submit(boundedExecutor)
  }

  override fun isModified(): Boolean = panel.isModified()

  override fun reset() = panel.reset()

  override fun apply() = panel.apply()
}
