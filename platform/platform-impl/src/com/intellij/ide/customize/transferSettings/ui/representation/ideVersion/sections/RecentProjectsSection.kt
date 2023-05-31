// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.models.SettingsPreferencesKind
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.dsl.builder.panel
import org.jetbrains.annotations.Nls
import javax.swing.JComponent

class RecentProjectsSection(private val ideVersion: IdeVersion) : IdeRepresentationSection(ideVersion.settings.preferences, SettingsPreferencesKind.RecentProjects, AllIcons.Plugins.PluginLogo) {
  private val recentProjects get() = ideVersion.settings.recentProjects
  override fun getContent(): JComponent {
    if (recentProjects.size > 5) {
      withMoreLabel {
        panel {
          recentProjects.drop(5).forEach {
            row {
              it.info.displayName?.let { it1: @NlsSafe String -> label(it1) }
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

  private fun createString(): @Nls String = StringBuilder().run {
    append(recentProjects.take(5).mapNotNull { it.info.displayName }.joinToString { it })
    if (recentProjects.size > 5) {
      append(" ")
      append(IdeBundle.message("transfersettings.projects.and.n.more", recentProjects.size-5))
    }
    toString() // NON-NLS because it's localised
  }

  override val name: String
    get() = "Recent Projects"

  override fun worthShowing(): Boolean = ideVersion.settings.recentProjects.isNotEmpty()
}