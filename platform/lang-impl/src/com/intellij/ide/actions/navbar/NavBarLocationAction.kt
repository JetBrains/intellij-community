// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.navbar

import com.intellij.ide.ui.NavBarLocation
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.ui.ExperimentalUI

internal abstract class NavBarLocationAction(private val location: NavBarLocation) : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.Frontend {
  init {
    templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.IfRequested
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

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
}

internal class NavBarTopLocationAction : NavBarLocationAction(NavBarLocation.TOP)

internal class NavBarBottomLocationAction : NavBarLocationAction(NavBarLocation.BOTTOM)

internal class HideNavBarAction : DumbAwareToggleAction(), ActionRemoteBehaviorSpecification.Frontend {
  init {
    templatePresentation.keepPopupOnPerform = KeepPopupOnPerform.IfRequested
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = !UISettings.getInstance().showNavigationBar

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    UISettings.getInstance().let {
      it.showNavigationBar = false
      it.fireUISettingsChanged()
    }
  }
}
