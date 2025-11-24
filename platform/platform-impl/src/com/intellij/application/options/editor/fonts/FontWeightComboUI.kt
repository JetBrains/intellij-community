// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("FontWeightComboUI")

package com.intellij.application.options.editor.fonts

import com.intellij.openapi.application.ApplicationBundle
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import javax.swing.ListCellRenderer

internal fun getRenderer(markRecommended: Boolean): ListCellRenderer<FontWeightCombo.MyWeightItem> = listCellRenderer {
  text(value.subFamily)

  if (markRecommended && value.isRecommended) {
    text(ApplicationBundle.message("settings.editor.font.recommended")) {
      foreground = greyForeground
    }
  }
}
