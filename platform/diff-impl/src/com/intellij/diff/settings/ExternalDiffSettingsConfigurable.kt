// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.settings

import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.layout.selected

internal class ExternalDiffSettingsConfigurable : BoundSearchableConfigurable(
  DiffBundle.message("configurable.ExternalDiffSettingsConfigurable.display.name"),
  "diff.external"
) {

  override fun createPanel(): DialogPanel {
    val settings = ExternalDiffSettings.instance
    return panel {
      lateinit var externalToolsEnabled: Cell<JBCheckBox>
      row {
        externalToolsEnabled = checkBox(DiffBundle.message("settings.external.diff.enable.external.tools"))
          .bindSelected(settings::isExternalToolsEnabled)
      }

      val models = ExternalToolsModels()
      indent {
        row {
          val treePanel = ExternalToolsTreePanel(models)
          cell(treePanel.component)
            .label(DiffBundle.message("settings.external.diff.panel.tree.title"), LabelPosition.TOP)
            .align(AlignX.FILL)
            .onIsModified { treePanel.onModified(settings) }
            .onApply { treePanel.onApply(settings) }
            .onReset { treePanel.onReset(settings) }
        }.bottomGap(BottomGap.MEDIUM)

        row {
          val externalToolsTablePanel = ExternalToolsTablePanel(models)
          cell(externalToolsTablePanel.component)
            .label(DiffBundle.message("settings.external.diff.panel.table.title"), LabelPosition.TOP)
            .align(AlignX.FILL)
            .onIsModified { externalToolsTablePanel.onModified(settings) }
            .onApply { externalToolsTablePanel.onApply(settings) }
            .onReset { externalToolsTablePanel.onReset(settings) }
        }
      }.enabledIf(externalToolsEnabled.component.selected)
    }
  }
}
