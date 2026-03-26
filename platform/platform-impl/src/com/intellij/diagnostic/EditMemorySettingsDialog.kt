// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.diagnostic.VMOptions.MemoryKind
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.options.OptionsBundle
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.MAX_LINE_LENGTH_NO_WRAP
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.text
import com.intellij.ui.dsl.validation.Level
import com.intellij.util.ui.IoErrorText
import com.sun.management.OperatingSystemMXBean
import java.awt.event.ActionEvent
import java.lang.management.ManagementFactory
import java.nio.file.Path
import javax.swing.Action
import javax.swing.JComponent

private const val HEAP_MIN_MB = 512
private const val HEAP_DEFAULT_MB = 2048
private const val HEAP_INCREMENT_MB = 512
private const val OTHER_MIN_MB = 256

internal open class EditMemorySettingsDialog(
  private val file: Path,
  private val memoryKind: MemoryKind,
  private val memoryLow: Boolean
) : DialogWrapper(true) {
  private val current = VMOptions.readOption(memoryKind, /*effective =*/ true)
  private val minValue = if (memoryKind == MemoryKind.HEAP) HEAP_MIN_MB else OTHER_MIN_MB
  private val memoryTotal = (ManagementFactory.getOperatingSystemMXBean() as OperatingSystemMXBean).totalMemorySize.ushr(20).toInt()

  private lateinit var newValueField: JBTextField
  private lateinit var saveAndExitAction: Action
  private lateinit var saveAndCloseAction: Action

  init {
    title = DiagnosticBundle.message("change.memory.title")
    init()
  }

  private fun getSuggestedValue(): Int {
    val suggested = if (memoryLow && memoryKind == MemoryKind.HEAP) {
      if (current > 0) current + HEAP_INCREMENT_MB else HEAP_DEFAULT_MB
    }
    else {
      val configured = VMOptions.readOption(memoryKind, /*effective =*/ false)
      if (configured > 0) configured else current
    }
    return suggested.coerceIn(minValue, memoryTotal)
  }

  override fun createCenterPanel(): JComponent = panel {
    if (memoryLow) {
      row {
        if (memoryKind == MemoryKind.HEAP) {
          val free = Runtime.getRuntime().freeMemory().ushr(20)
          val max = Runtime.getRuntime().maxMemory().ushr(20)
          label(DiagnosticBundle.message("change.memory.usage", free.toString(), max.toString()))
        }
        else {
          label(DiagnosticBundle.message("change.memory.message"))
        }
      }
    }

    row {
      label(DiagnosticBundle.message("change.memory.act"))
    }

    @Suppress("UseHtmlChunkToolTip")
    val optionLabel = JBLabel(memoryKind.label() + ':').apply { toolTipText = '-' + memoryKind.optionName }
    row(optionLabel) {
      newValueField = textField()
        .text(getSuggestedValue().toString())
        .columns(5)
        .gap(RightGap.SMALL)
        .focused()
        .cellValidation {
          addInputRule(DiagnosticBundle.message("change.memory.low", minValue)) {
            val value = it.text.toIntOrNull()
            val invalid = value == null || value < minValue
            saveAndExitAction.isEnabled = !invalid
            saveAndCloseAction.isEnabled = !invalid
            invalid
          }
          addInputRule(DiagnosticBundle.message("change.memory.high", memoryTotal.toString()), level = Level.WARNING) {
            val value = it.text.toIntOrNull()
            value != null && value > memoryTotal
          }
        }
        .component

      val formatted = if (current == -1) DiagnosticBundle.message("change.memory.unknown") else current.toString()
      text(DiagnosticBundle.message("change.memory.units", formatted))
    }

    row {
      icon(AllIcons.General.Information)
        .align(AlignY.TOP)
        .gap(RightGap.SMALL)
      text(DiagnosticBundle.message("change.memory.file", file.toString()), maxLineLength = MAX_LINE_LENGTH_NO_WRAP)
    }
  }

  override fun createActions(): Array<Action?> {
    val canRestart = ApplicationManager.getApplication().isRestartCapable()
    saveAndExitAction = object : DialogWrapperAction(DiagnosticBundle.message(if (canRestart) "change.memory.apply" else "change.memory.exit")) {
      override fun doAction(e: ActionEvent?) {
        if (save()) {
          (ApplicationManager.getApplication() as ApplicationEx).restart(true)
        }
      }
    }
    saveAndCloseAction = object : DialogWrapperAction(IdeBundle.message("button.save")) {
      override fun doAction(e: ActionEvent?) {
        if (save()) {
          close(OK_EXIT_CODE)
        }
      }
    }
    return arrayOf(saveAndExitAction, saveAndCloseAction, cancelAction)
  }

  override fun getPreferredFocusedComponent(): JComponent = newValueField

  private fun save(): Boolean {
    try {
      val value = newValueField.getText().toInt()
      EditMemorySettingsService.getInstance().save(memoryKind, value)
      return true
    }
    catch (e: Exception) {
      Messages.showErrorDialog(newValueField, IoErrorText.message(e), OptionsBundle.message("cannot.save.settings.default.dialog.title"))
      return false
    }
  }
}
