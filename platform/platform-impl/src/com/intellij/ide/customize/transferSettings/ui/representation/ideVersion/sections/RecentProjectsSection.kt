// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections

import com.intellij.icons.AllIcons
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.models.SettingsPreferencesKind
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

class RecentProjectsSection(private val ideVersion: IdeVersion) : IdeRepresentationSection(ideVersion.settings.preferences, SettingsPreferencesKind.RecentProjects, AllIcons.Plugins.PluginLogo) {
  private val recentProjects get() = ideVersion.settings.recentProjects
  override fun getContent(): JComponent {
    if (recentProjects.size > 5) {
      withMoreLabel {
        panel {
          recentProjects.drop(5).forEach {
            row {
              it.info.displayName?.let { it1 -> label(it1) }
            }
          }
        }
      }
    }
    return panel {
      row {
        mutableLabel(createString())
      }
    }
  }

  private fun createString() = StringBuilder().run {
    append(recentProjects.take(5).mapNotNull { it.info.displayName }.joinToString { it })
    if (recentProjects.size > 5) {
      append(" and ${recentProjects.size-5} more")
    }
    toString()
  }

  override val name: String
    get() = "Recent Projects"

  override fun worthShowing() = ideVersion.settings.recentProjects.isNotEmpty()
}