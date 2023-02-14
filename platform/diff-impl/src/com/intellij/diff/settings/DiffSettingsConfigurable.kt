/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.openapi.diff.DiffBundle.message
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindValue
import com.intellij.ui.dsl.builder.labelTable
import com.intellij.ui.dsl.builder.panel
import javax.swing.JLabel

class DiffSettingsConfigurable : BoundSearchableConfigurable(
  message("configurable.DiffSettingsConfigurable.display.name"),
  "diff.base"
) {

  override fun createPanel(): DialogPanel {
    val textSettings = TextDiffSettings.getSettings()
    val diffSettings = DiffSettings.getSettings()

    return panel {
      group(message("settings.diff.name")) {
        row(message("settings.context.lines")) {
          slider(0, TextDiffSettingsHolder.CONTEXT_RANGE_MODES.size - 1, 1, 1)
            .labelTable(TextDiffSettingsHolder.CONTEXT_RANGE_MODES.indices.associateWith { index ->
              JLabel(TextDiffSettingsHolder.CONTEXT_RANGE_MODE_LABELS[index])
            })
            .bindValue(
              { TextDiffSettingsHolder.CONTEXT_RANGE_MODES.indexOf(textSettings.contextRange).coerceAtLeast(0) },
              { textSettings.contextRange = TextDiffSettingsHolder.CONTEXT_RANGE_MODES[it] }
            )
        }
        row {
          checkBox(message("settings.go.to.the.next.file.after.reaching.last.change"))
            .bindSelected(diffSettings::isGoToNextFileOnNextDifference)
        }
      }
      group(message("settings.merge.text")) {
        row {
          checkBox(message("settings.automatically.apply.non.conflicting.changes"))
            .bindSelected(textSettings::isAutoApplyNonConflictedChanges)
        }
        row {
          checkBox(message("settings.highlight.modified.lines.in.gutter"))
            .bindSelected(textSettings::isEnableLstGutterMarkersInMerge)
        }
      }
    }
  }
}