// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight

import com.intellij.java.JavaBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.ToolbarDecorator
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls

internal fun createAnnotationsPanel(toolbarDecorator: ToolbarDecorator, name: @Nls String): DialogPanel {
  return panel {
    row {
      cell(toolbarDecorator.createPanel())
        .label(JavaBundle.message("annotations.panel.title", name), LabelPosition.TOP)
        .align(Align.FILL)
    }.resizableRow()
  }.withPreferredHeight(200)
}
