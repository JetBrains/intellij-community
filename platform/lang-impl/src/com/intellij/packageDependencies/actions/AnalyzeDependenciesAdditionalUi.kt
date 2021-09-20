// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packageDependencies.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import javax.swing.JCheckBox
import javax.swing.JSpinner

internal class AnalyzeDependenciesAdditionalUi {
  val transitiveCB: JCheckBox = JBCheckBox()
  val borderChooser: JSpinner = JSpinner()
  val panel = panel {
    group(CodeInsightBundle.message("analysis.options")) {
      row {
        cell(transitiveCB)
      }
      indent {
        row(CodeInsightBundle.message("analyze.dependencies.transitive.dependencies.label")) {
          cell(borderChooser)
        }
      }
    }
  }
}