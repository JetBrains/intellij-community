// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.ui.configuration

import com.intellij.ide.JavaUiBundle
import com.intellij.ui.dsl.builder.panel
import javax.swing.JButton

internal class ModuleJdkConfigurableUi(moduleCombo: JdkComboBox, editButton: JButton) {
  val panel = panel {
    row(JavaUiBundle.message("module.libraries.target.jdk.module.radio")) {
      cell(moduleCombo)
      cell(editButton)
    }
  }
}