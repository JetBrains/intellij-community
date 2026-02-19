// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.pathMacros

import com.intellij.ide.IdeBundle
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Internal
internal class PathMacroListEditorUI(table: JComponent) {

  lateinit var ignoredVariables: JBTextField

  val content = panel {
    row {
      cell(table)
        .align(Align.FILL)
    }.resizableRow()

    row(IdeBundle.message("path.macro.ignored.variables")) {
      ignoredVariables = textField()
        .align(AlignX.FILL)
        .comment(IdeBundle.message("path.macro.use.semicolon"))
        .component
    }
  }
}