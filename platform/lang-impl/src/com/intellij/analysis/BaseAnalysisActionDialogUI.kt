// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.dsl.builder.RightGap
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls
import javax.swing.JCheckBox
import javax.swing.JPanel
import javax.swing.JRadioButton

internal class BaseAnalysisActionDialogUI {

  fun panel(@Nls scopeTitle: String,
            viewItems: List<ModelScopeItemView>,
            inspectTestSource: JCheckBox,
            analyzeInjectedCode: JCheckBox,
            buttons: ArrayList<JRadioButton>,
            disposable: Disposable): JPanel {

    return panel {
      group(scopeTitle, topGroupGap = false) {
        for (item in viewItems) {
          row {
            buttons.add(item.button)
            cell(item.button).apply {
              if (item.additionalComponents.any()) gap(RightGap.SMALL)
            }
            for (component in item.additionalComponents) {
              if (component is Disposable) {
                Disposer.register(disposable, component)
              }
              cell(component)
            }
          }
        }

        row {
          cell(inspectTestSource)
          cell(analyzeInjectedCode)
        }
      }
    }
  }

}