// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.analysis

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.ui.layout.*
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
      titledRow(scopeTitle) {
        for (item in viewItems) {
          row {
            cell {

              buttons.add(item.button)
              item.button()
              for (component in item.additionalComponents) {
                if (component is Disposable) {
                  Disposer.register(disposable, component)
                }
                component()
              }
            }
          }
        }


        row {
          cell {
            inspectTestSource()
            analyzeInjectedCode()
          }
        }
      }
    }
  }

}