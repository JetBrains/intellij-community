// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.packageDependencies.actions

import com.intellij.codeInsight.CodeInsightBundle
import com.intellij.ui.JBIntSpinner
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.*

internal class AnalyzeDependenciesAdditionalUi {
  lateinit var transitiveCB: JBCheckBox
  lateinit var borderChooser: JBIntSpinner

  val panel = panel {
    group(CodeInsightBundle.message("analysis.options")) {
      row {
        transitiveCB = checkBox(CodeInsightBundle.message("analyze.dependencies.transitive.dependencies.checkbox"))
          .component
      }
      indent {
        row(CodeInsightBundle.message("analyze.dependencies.transitive.dependencies.label")) {
          borderChooser = spinner(0..99999)
            .enabledIf(transitiveCB.selected)
            .component
        }
      }
    }
  }
}