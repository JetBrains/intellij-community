// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packageDependencies.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.layout.*
import javax.swing.JCheckBox
import javax.swing.JSpinner

internal class AnalyzeDependenciesSettingPanelUi {
  val transitiveCB: JCheckBox = JBCheckBox()
  val borderChooser: JSpinner = JSpinner()
  val panel = panel {
    titledRow(CodeInsightBundle.message("analysis.options")) {
      row {
        transitiveCB()
        borderChooser().constraints(pushX, growX)
      }
    }
  }
}