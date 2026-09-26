// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.ui.tree

import com.intellij.openapi.ui.DialogPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import javax.swing.JPanel

internal class PerFileConfigurableBaseUi<T> {

  fun getPanel(
    tablePanel: JPanel,
    tableComment: @NlsContexts.DetailedDescription String,
    defaultProps: List<PerFileConfigurableBase.Mapping<T>>,
    actionPanelProvider: (PerFileConfigurableBase.Mapping<T>) -> JPanel,
  ): DialogPanel = panel {
    for (prop in defaultProps) {
      row(prop.name + ":") {
        cell(actionPanelProvider(prop))
      }
    }

    row {
      cell(tablePanel)
        .align(Align.FILL)
        .apply {
          if (tableComment.isNotEmpty()) { comment(tableComment) }
        }
    }.resizableRow()
  }

}