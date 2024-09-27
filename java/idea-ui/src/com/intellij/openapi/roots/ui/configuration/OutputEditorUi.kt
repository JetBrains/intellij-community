// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.util.ui.JBUI

class OutputEditorUi {
  fun createPanel(compilerOutputEditor: BuildElementsEditor, javadocEditor: JavadocEditor, annotationsEditor: AnnotationsEditor): DialogPanel = panel {
    row {
      cell(compilerOutputEditor.component)
        .align(AlignX.FILL)
        .gap(RightGap.COLUMNS)
    }
      .bottomGap(BottomGap.MEDIUM)
    row {
      @Suppress("DialogTitleCapitalization")
      cell(javadocEditor.component)
        .align(Align.FILL)
        .label(javadocEditor.displayName, LabelPosition.TOP)
    }
      .resizableRow()
      .bottomGap(BottomGap.MEDIUM)
    row {
      @Suppress("DialogTitleCapitalization")
      cell(annotationsEditor.component)
        .align(Align.FILL)
        .label(annotationsEditor.displayName, LabelPosition.TOP)
    }.resizableRow()
  }.withBorder(JBUI.Borders.empty(8))

}