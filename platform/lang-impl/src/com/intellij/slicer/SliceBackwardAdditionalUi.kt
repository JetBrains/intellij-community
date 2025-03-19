// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer

import com.intellij.lang.LangBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.ApiStatus
import javax.swing.JTextField

@ApiStatus.Internal
class SliceBackwardAdditionalUi {
  lateinit var field: JTextField
  val panel: DialogPanel = panel {
    row(LangBundle.message("label.filter.value")) {
      field = textField()
        .align(AlignX.FILL)
        .component
    }
  }
}