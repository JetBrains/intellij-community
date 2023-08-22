// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.navbar

import com.intellij.ide.ui.NavBarLocation
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.ExperimentalUI

abstract class NavBarLocationAction(private val location: NavBarLocation) : ToggleAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  override fun isSelected(e: AnActionEvent): Boolean {
    val settings = UISettings.getInstance()
    return settings.showNavigationBar && settings.navBarLocation == location
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    UISettings.getInstance().let {
      it.navBarLocation = location
      it.showNavigationBar = true
      it.fireUISettingsChanged()
    }
  }

  override fun update(e: AnActionEvent) {
    if (!ExperimentalUI.isNewUI()) {
      e.presentation.isEnabledAndVisible = false
      return
    }
    super.update(e)
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

class NavBarTopLocationAction : NavBarLocationAction(NavBarLocation.TOP)
class NavBarBottomLocationAction : NavBarLocationAction(NavBarLocation.BOTTOM)
class HideNavBarAction : ToggleAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  override fun isSelected(e: AnActionEvent): Boolean = !UISettings.getInstance().showNavigationBar

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    UISettings.getInstance().let {
      it.showNavigationBar = false
      it.fireUISettingsChanged()
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
