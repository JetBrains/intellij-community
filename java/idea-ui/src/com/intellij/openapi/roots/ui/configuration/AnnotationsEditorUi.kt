// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.ide.JavaUiBundle
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.JPanel

class AnnotationsEditorUi {
  fun createPanel(table: JPanel) = panel {
    row {
      cell(table)
        .align(Align.FILL)
        .comment(JavaUiBundle.message("project.roots.external.annotations.description"))
    }.resizableRow()
  }
}