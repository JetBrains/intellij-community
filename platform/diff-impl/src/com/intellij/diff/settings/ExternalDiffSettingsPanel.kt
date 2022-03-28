/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.diff.settings

import com.intellij.diff.tools.external.ExternalDiffSettings
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.BottomGap
import com.intellij.ui.dsl.builder.Cell
import com.intellij.ui.dsl.builder.LabelPosition
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.HorizontalAlign
import com.intellij.ui.layout.*
import javax.swing.JComponent

class ExternalDiffSettingsPanel {
  private val panel: DialogPanel

  init {
    val settings = ExternalDiffSettings.instance

    panel = panel {
      lateinit var externalToolsEnabled: Cell<JBCheckBox>
      row {
        externalToolsEnabled = checkBox(DiffBundle.message("settings.external.diff.enable.external.tools"))
          .bindSelected(settings::isExternalToolsEnabled)
      }

      val models = ExternalToolsModels()
      val externalToolsTablePanel = ExternalToolsTablePanel(models)
      indent {
        row {
          val treePanel = ExternalToolsTreePanel(models)
          cell(treePanel.component)
            .label(DiffBundle.message("settings.external.diff.panel.tree.title"), LabelPosition.TOP)
            .horizontalAlign(HorizontalAlign.FILL)
            .onIsModified { treePanel.onModified(settings) }
            .onApply { treePanel.onApply(settings) }
            .onReset { treePanel.onReset(settings) }
        }.bottomGap(BottomGap.MEDIUM)

        row {
          cell(externalToolsTablePanel.component)
            .label(DiffBundle.message("settings.external.diff.panel.table.title"), LabelPosition.TOP)
            .horizontalAlign(HorizontalAlign.FILL)
            .onIsModified { externalToolsTablePanel.onModified(settings) }
            .onApply { externalToolsTablePanel.onApply(settings) }
            .onReset { externalToolsTablePanel.onReset(settings) }
        }
      }.enabledIf(externalToolsEnabled.component.selected)
    }
  }

  fun createComponent(): JComponent {
    return panel
  }

  fun isModified(): Boolean {
    return panel.isModified()
  }

  fun apply() {
    panel.apply()
  }

  fun reset() {
    panel.reset()
  }
}
