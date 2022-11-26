// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections

import com.intellij.icons.AllIcons
import com.intellij.ide.customize.transferSettings.models.*
import com.intellij.ui.dsl.builder.*
import com.intellij.ui.dsl.gridLayout.Gaps
import com.intellij.ui.dsl.gridLayout.VerticalGaps
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

class PluginsSection(private val ideVersion: IdeVersion) : IdeRepresentationSection(ideVersion.settings.preferences, SettingsPreferencesKind.Plugins, AllIcons.Plugins.PluginLogo) {
  override val name = "Plugins and Features"
  override fun worthShowing() = ideVersion.settings.plugins.isNotEmpty()

  override fun getContent(): JComponent {
    if (ideVersion.settings.plugins.size > 3) {
      withMoreLabel {
        return@withMoreLabel panel {
          for (plugin in ideVersion.settings.plugins.drop(3)) {
            createPluginRow(plugin, this)
          }
        }
      }
    }
    return panel {
      for (plugin in ideVersion.settings.plugins) {
        createPluginRow(plugin, this)
      }
    }
  }


  private fun createPluginRow(plugin: FeatureInfo, panel: Panel): Row {
    return panel.row {
      label(plugin.name).bold().customize(Gaps(right = 4)).applyToComponent {
        _isSelected.afterChange {
          this.foreground = if (it) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
        }
      }

      comment(
        when (plugin) {
          is BuiltInFeature -> "built-in"
          is PluginFeature -> "plugin"
          else -> ""
        }
      )
    }.customize(VerticalGaps())
  }
}