// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.slicer

import com.intellij.lang.LangBundle
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox

class SliceForwardAdditionalUi {
  lateinit var myShowDerefs: JCheckBox
  val panel = panel {
    row {
      myShowDerefs = checkBox(LangBundle.message("checkbox.show.method.calls.and.field.accesses.on.variable.being.analysed")).component
    }
  }
}