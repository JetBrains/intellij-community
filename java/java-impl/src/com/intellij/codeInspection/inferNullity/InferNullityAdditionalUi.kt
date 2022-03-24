// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.inferNullity

import com.intellij.java.JavaBundle
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox

class InferNullityAdditionalUi {
  lateinit var checkBox: JCheckBox
  val panel = panel {
    row {
      checkBox = checkBox(JavaBundle.message("checkbox.annotate.local.variables")).component
    }
  }

}