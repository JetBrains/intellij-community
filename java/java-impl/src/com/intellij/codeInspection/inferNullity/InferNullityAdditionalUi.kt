// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInspection.inferNullity

import com.intellij.java.JavaBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox

public class InferNullityAdditionalUi {
  public lateinit var checkBox: JCheckBox
  public val panel: DialogPanel = panel {
    row {
      checkBox = checkBox(JavaBundle.message("checkbox.annotate.local.variables")).component
    }
  }

}