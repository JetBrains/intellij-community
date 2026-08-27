// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.ide.plugins.marketplace.statistics.enums.PluginManagerOpenSourceEnum
import com.intellij.ide.plugins.newui.TabbedPaneHeaderComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.UIBundle
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Component

@ApiStatus.Internal
object PluginManagerConfigurableUtils {
  fun showInstallPluginDialog(component: Component, filter: @NlsSafe String) {
    val configurable = PluginManagerConfigurable()
    AutoCloseable(configurable::disposeUIResources).use {
      configurable.openMarketplaceTab(filter)
      val dialogBuilder = DialogBuilder(component)
      dialogBuilder.title(UIBundle.message("newProjectWizard.ProjectTypeStep.InstallPluginAction.title"))
      dialogBuilder.centerPanel(
        JBUI.Panels.simplePanel(configurable.createComponent().apply {
          border = JBUI.Borders.customLine(JBColor.border(), 0, 1, 1, 1)
        }).addToTop(configurable.topComponent.apply {
          if (this is TabbedPaneHeaderComponent) {
            setWelcomeScreen(true)
          }
        })
      )
      dialogBuilder.addOkAction()
      dialogBuilder.addCancelAction()
      if (dialogBuilder.showAndGet() && configurable.isModified) {
        configurable.apply()
      }
    }
  }

  @JvmStatic
  fun showInstalledTabWithSearch(
    project: Project?,
    searchQuery: @NlsSafe String,
    openSource: PluginManagerOpenSourceEnum? = null,
  ) {
    val configurable = PluginManagerConfigurable().apply {
      if (openSource != null) {
        setOpenSource(openSource)
      }
    }
    ShowSettingsUtil.getInstance().editConfigurable(project, configurable, Runnable {
      configurable.openInstalledTab("")
      val search = configurable.enableSearch(searchQuery, true)
      if (search != null) {
        ApplicationManager.getApplication().invokeLater(search)
      }
    })
  }
}
