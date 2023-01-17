// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.runToolbar

import com.intellij.ide.ui.ToolbarSettings
import com.intellij.ide.ui.UISettings.Companion.getInstance
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

/**
 * Used for Rider
 * TODO should be removed in 232
 */
class RunToolbarRollbackToPrevious : DumbAwareAction() {
  companion object {

    private const val ROLLBACK_AVAILABILITY = "ide.widget.toolbar.rollback.availability"
    private const val INCLUSION_NAVBAR_STATE = "ide.widget.toolbar.first.inclusion.navbar.state"
    private const val INCLUSION_TOOLBAR_STATE = "ide.widget.toolbar.first.inclusion.toolbar.state"

    /**
     * Used for Rider
     */
    fun saveDataIfNeeded(isFirstSessionForNewUser: Boolean) {
      val properties = PropertiesComponent.getInstance()

      if (isFirstSessionForNewUser) {
        properties.setValue(ROLLBACK_AVAILABILITY, false)
      } else {
        properties.setValue(ROLLBACK_AVAILABILITY, true)
        properties.setValue(INCLUSION_NAVBAR_STATE, getInstance().showNavigationBar)
        properties.setValue(INCLUSION_TOOLBAR_STATE, getInstance().showMainToolbar)
      }
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val properties = PropertiesComponent.getInstance()
    e.presentation.isEnabledAndVisible = properties.getBoolean(ROLLBACK_AVAILABILITY)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val properties = PropertiesComponent.getInstance()

    ToolbarSettings.getInstance().isVisible = false
    val uiSettings = getInstance()

    uiSettings.showNavigationBar = properties.getBoolean(INCLUSION_NAVBAR_STATE)
    uiSettings.showMainToolbar = properties.getBoolean(INCLUSION_TOOLBAR_STATE)
    uiSettings.fireUISettingsChanged()

    properties.setValue(ROLLBACK_AVAILABILITY, false)
  }
}