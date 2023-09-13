// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.customize.transferSettings.models.*
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.Panel
import com.intellij.ui.dsl.builder.Row
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

class PluginsSection(private val ideVersion: IdeVersion) : IdeRepresentationSection(ideVersion.settingsCache.preferences, SettingsPreferencesKind.Plugins, AllIcons.TransferSettings.PluginsAndFeatures) {
  private val plugins = ideVersion.settingsCache.plugins.filter { !it.isHidden }
  override val name: String = "Plugins and Features"
  override fun worthShowing(): Boolean = plugins.isNotEmpty()

  override fun getContent(): JComponent {
    if (plugins.size > LIMIT) {
      withMoreLabel("and ${plugins.size - LIMIT} more") {
        return@withMoreLabel JBScrollPane(Wrapper(panel {
          for (plugin in plugins.drop(LIMIT)) {
            createPluginRow(plugin, this)
          }
        }).apply { border = JBUI.Borders.empty(5) }, JBScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
      }
    }
    return panel {
      for (plugin in plugins.take(LIMIT)) {
        createPluginRow(plugin, this)
      }
    }
  }


  private fun createPluginRow(plugin: FeatureInfo, panel: Panel): Row {
    return panel.row {
      label(plugin.name).bold().customize(UnscaledGaps(right = 4)).applyToComponent {
        _isSelected.afterChange {
          this.foreground = if (it) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
        }
        _isEnabled.afterChange {
          this.foreground = if (it || !_isSelected.get()) UIUtil.getLabelForeground() else UIUtil.getLabelDisabledForeground()
        }
      }

      comment(
        when (plugin) {
          is BuiltInFeature -> IdeBundle.message("transfersettings.plugin.built.in")
          is PluginFeature -> IdeBundle.message("transfersettings.plugin.plugin")
          else -> ""
        }
      )
    }.customize(UnscaledGapsY())
  }
}