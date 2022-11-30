// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.*
import com.intellij.util.system.CpuArch
import kotlin.math.max

private const val MIN_VALUE = 256

internal class EditMemorySettingsPanel(private val option: VMOptions.MemoryKind, private val memoryLow: Boolean) {

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
      val suggested = getSuggested(current)
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

  private fun getSuggested(current: Int): Int {
    if (memoryLow && option == VMOptions.MemoryKind.HEAP) {
      val cap = if (CpuArch.isIntel32()) 800 else Registry.intValue("max.suggested.heap.size")
      if (current > 0) {
        val result = current + EditMemorySettingsDialog.HEAP_INC
        return if (result > cap) max(cap, current) else result
      }
      else {
        return cap
      }
    }
    else {
      var result = VMOptions.readOption(option, false)
      if (result <= 0) result = current
      if (result <= 0) result = MIN_VALUE
      return result
    }
  }
}
