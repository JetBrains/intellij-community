// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.settings

import com.intellij.icons.AllIcons
import com.intellij.java.JavaBundle
import com.intellij.java.library.JavaLibraryUtil
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.util.concurrency.AppExecutorUtil
import javax.swing.JPanel

class JvmLoggingConfigurablePanel(
  private val project: Project,
  private val settings: JavaSettingsStorage.State) {
  private lateinit var warningRow: Row
  private lateinit var panel: DialogPanel
  private val boundedExecutor = AppExecutorUtil.getAppExecutorService()

  fun getMainPanel(): JPanel {
    panel = panel {
      group(JavaBundle.message("jvm.logging.configurable.java.group.display.name")) {
        row {
          label(JavaBundle.message("label.configurable.logger.type"))
          comboBox(JavaLoggerModel(JavaLoggerInfo.allLoggers, settings.logger)).bindItem(settings::logger.toNullableProperty()).onChanged {
            updateWarningRow(it.item.loggerName)
          }
        }
        warningRow = row {
          icon(AllIcons.General.Warning).align(AlignY.TOP).gap(rightGap = RightGap.SMALL)
          text(JavaBundle.message("java.configurable.logger.not.found"))
        }.visible(false)
      }
    }
    updateWarningRow(settings.logger.loggerName)
    return panel
  }

  private fun updateWarningRow(loggerName : String) {
    ReadAction.nonBlocking<Boolean> {
      !JavaLibraryUtil.hasLibraryClass(project, loggerName)
    }.finishOnUiThread(ModalityState.any()) { isVisible ->
      warningRow.visible(isVisible)
    }.submit(boundedExecutor)
  }

  fun dispose() = boundedExecutor.shutdown()

  fun isModified(): Boolean = panel.isModified()

  fun apply() = panel.apply()

  fun reset() = panel.reset()
}