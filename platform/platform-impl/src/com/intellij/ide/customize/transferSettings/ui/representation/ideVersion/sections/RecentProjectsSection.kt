// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customize.transferSettings.ui.representation.ideVersion.sections

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.customize.transferSettings.models.IdeVersion
import com.intellij.ide.customize.transferSettings.models.RecentPathInfo
import com.intellij.ide.customize.transferSettings.models.SettingsPreferencesKind
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

class RecentProjectsSection(private val ideVersion: IdeVersion) : IdeRepresentationSection(ideVersion.settingsCache.preferences, SettingsPreferencesKind.RecentProjects, AllIcons.TransferSettings.RecentProjects) {
  private val recentProjects get() = ideVersion.settingsCache.recentProjects
  override fun getContent(): JComponent {
    if (recentProjects.size > LIMIT) {
      withMoreLabel(IdeBundle.message("transfersettings.projects.and.n.more", recentProjects.size - LIMIT)) {
        Wrapper(JBScrollPane(getPopupScrollContent()).apply {
          verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS
          horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
          border = JBUI.Borders.empty()
        })
      }
    }
    return panel {
      row {
        mutableLabel(createString(recentProjects.take(LIMIT)))
      }
    }
  }

  private fun getPopupScrollContent() = panel {
    row {
      text(createString(recentProjects.drop(LIMIT)))
    }
  }

  private fun createString(pr: List<RecentPathInfo>): @Nls String = pr.mapNotNull { it.info.displayName }.joinToString { it }

  override val name: String
    get() = "${recentProjects.size} Recent Projects"

  override fun worthShowing(): Boolean = recentProjects.isNotEmpty()
}