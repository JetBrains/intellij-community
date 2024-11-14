// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.settings

import com.intellij.diff.impl.DiffSettingsHolder.DiffSettings
import com.intellij.diff.impl.DiffSettingsHolder.IncludeInNavigationHistory
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.openapi.diff.DiffBundle.message
import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import javax.swing.JLabel

internal class DiffSettingsConfigurable : BoundSearchableConfigurable(
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
        row {
          label(message("settings.diffIncludedInHistory"))
          comboBox(IncludeInNavigationHistory.entries, textListCellRenderer("") { option ->
            when (option) {
              IncludeInNavigationHistory.Always -> message("settings.diffIncludedInHistory.always")
              IncludeInNavigationHistory.OnlyIfOpen -> message("settings.diffIncludedInHistory.onlyIfOpen")
              IncludeInNavigationHistory.Never -> message("settings.diffIncludedInHistory.never")
            }
          }).bindItem(diffSettings::isIncludedInNavigationHistory.toNullableProperty())
        }
      }
      group(message("settings.merge.text")) {
        row {
          checkBox(message("settings.automatically.apply.non.conflicting.changes"))
            .bindSelected(textSettings::isAutoApplyNonConflictedChanges)
        }
        row {
          checkBox(message("settings.automatically.resolve.imports.conflicts"))
            .bindSelected(textSettings::isAutoResolveImportConflicts)
        }
        row {
          checkBox(message("settings.highlight.modified.lines.in.gutter"))
            .bindSelected(textSettings::isEnableLstGutterMarkersInMerge)
        }
      }
    }
  }
}
