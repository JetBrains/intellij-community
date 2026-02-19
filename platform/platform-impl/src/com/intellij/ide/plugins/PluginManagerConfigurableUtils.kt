// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.plugins

import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.JBColor
import com.intellij.ui.UIBundle
import com.intellij.util.ui.JBDimension
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
          preferredSize = JBDimension(preferredSize.width, 40)
        })
      )
      dialogBuilder.addOkAction()
      dialogBuilder.addCancelAction()
      if (dialogBuilder.showAndGet() && configurable.isModified) {
        configurable.apply()
      }
    }
  }
}
