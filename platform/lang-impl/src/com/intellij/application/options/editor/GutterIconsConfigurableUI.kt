// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.application.options.editor

import com.intellij.codeInsight.daemon.GutterIconDescriptor
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.CheckBoxList
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel

internal class GutterIconsConfigurableUI(val list: CheckBoxList<GutterIconDescriptor?>) {

  @JvmField
  val panel: DialogPanel = panel {
    row {
      showGutterIconsJBCheckBox = checkBox(ApplicationBundle.message("checkbox.show.gutter.icons"))
        .component
    }

    row {
      scrollCell(list)
        .align(Align.FILL)
    }.resizableRow()
  }

  lateinit var showGutterIconsJBCheckBox: JBCheckBox

}
