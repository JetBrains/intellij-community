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
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.layout.selected

class ExternalDiffSettingsConfigurable : BoundSearchableConfigurable(
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
