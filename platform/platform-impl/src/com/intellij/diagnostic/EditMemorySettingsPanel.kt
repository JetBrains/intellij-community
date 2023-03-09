// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.icons.AllIcons
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*

internal class EditMemorySettingsPanel(private val option: VMOptions.MemoryKind, private val memoryLow: Boolean, private val suggested: Int) {
  lateinit var newValueField: JBTextField

  @JvmField
  val panel = panel {
    val current = VMOptions.readOption(option, true)
    val file = EditMemorySettingsService.getInstance().userOptionsFile ?: throw IllegalStateException()

    if (memoryLow) {
      row {
        if (option == VMOptions.MemoryKind.HEAP) {
          val free = Runtime.getRuntime().freeMemory() shr 20
          val max = Runtime.getRuntime().maxMemory() shr 20
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

    val optionLabel = JBLabel(option.label() + ':')
      .apply { toolTipText = '-' + option.optionName }
    row(optionLabel) {
      newValueField = textField()
        .text(suggested.toString())
        .columns(5)
        .gap(RightGap.SMALL)
        .focused()
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
}
