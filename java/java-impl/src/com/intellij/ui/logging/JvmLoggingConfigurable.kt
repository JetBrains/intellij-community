// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.logging

import com.intellij.icons.AllIcons
import com.intellij.java.JavaBundle
import com.intellij.lang.LangBundle
import com.intellij.lang.logging.JvmLogger
import com.intellij.lang.logging.JvmLoggerFieldDelegate
import com.intellij.lang.logging.UnspecifiedLogger
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable.NoScroll
import com.intellij.openapi.options.DslConfigurableBase
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.psi.PsiNameHelper
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.concurrency.AppExecutorUtil


class JvmLoggingConfigurable(private val project: Project) : DslConfigurableBase(), SearchableConfigurable, NoScroll {
  private lateinit var warningRow: Row
  private lateinit var loggerName: Cell<JBTextField>
  private val settings = project.service<JvmLoggingSettingsStorage>().state

  override fun getDisplayName(): String = JavaBundle.message("jvm.logging.configurable.display.name")

  override fun getId(): String = JavaBundle.message("jvm.logging.configurable.id")

  override fun createPanel(): DialogPanel {
    val loggers = JvmLogger.getAllLoggers(settings.loggerId == UnspecifiedLogger.UNSPECIFIED_LOGGER_ID)
    val panel = panel {
      group(JavaBundle.message("jvm.logging.configurable.java.group.display.name")) {
        row(JavaBundle.message("label.configurable.logger.generation.variable.name")) {
          loggerName = textField()
            .bindText(settings::loggerName.toNonNullableProperty(JvmLoggerFieldDelegate.LOGGER_IDENTIFIER))
            .validationOnInput {
              if (PsiNameHelper.getInstance(project).isIdentifier(it.text)) {
                return@validationOnInput null
              }
              error(LangBundle.message("dialog.message.valid.identifier", it.text))
            }
            .widthGroup(JavaBundle.message("jvm.logging.configurable.java.group.display.name"))
        }
        row(JavaBundle.message("label.configurable.logger.type")) {
          comboBox(loggers)
            .bindItem({ JvmLogger.getLoggerById(settings.loggerId) },
                      { settings.loggerId = it?.id })
            .onChanged { updateWarningRow(it.item) }
            .widthGroup(JavaBundle.message("jvm.logging.configurable.java.group.display.name"))
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

  override fun isModified(): Boolean {
    return PsiNameHelper.getInstance(project).isIdentifier(loggerName.component.text) && super<DslConfigurableBase>.isModified();
  }

  private fun updateWarningRow(logger: JvmLogger?) {
    ReadAction.nonBlocking<Boolean> { logger?.isAvailable(project) == false && logger !is UnspecifiedLogger }
      .finishOnUiThread(ModalityState.any()) { isVisible -> warningRow.visible(isVisible) }
      .submit(AppExecutorUtil.getAppExecutorService())
  }
}
