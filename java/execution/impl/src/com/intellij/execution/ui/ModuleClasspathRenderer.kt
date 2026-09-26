// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.ui

import com.intellij.ui.dsl.listCellRenderer.LcrInitParams
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import org.jetbrains.annotations.Nls
import java.util.function.Supplier
import javax.swing.ListCellRenderer

internal fun createRenderer(
  firstOptionItem: ModuleClasspathCombo.Item?,
  noModuleName: Supplier<@Nls String?>,
): ListCellRenderer<ModuleClasspathCombo.Item?> {
  val monospacedFont = CommonParameterFragments.getMonospaced()

  return listCellRenderer {
    font = monospacedFont
    copyWholeRow = true

    val value = value
    if (value == null) {
      noModuleName.get()?.let {
        text(it)
      }
      return@listCellRenderer
    }

    if (value == firstOptionItem) {
      separator { }
    }

    if (value.optionName != null) {
      text(value.optionName) {
        align = LcrInitParams.Align.LEFT
      }
      switch(value.myOptionValue)
      return@listCellRenderer
    }

    val name = value.module?.name ?: noModuleName.get()
    if (index == -1 && name != null) {
      @Suppress("HardCodedStringLiteral")
      text("-cp") {
        foreground = greyForeground
      }
    }
    name?.let { text(it) }
  }
}
