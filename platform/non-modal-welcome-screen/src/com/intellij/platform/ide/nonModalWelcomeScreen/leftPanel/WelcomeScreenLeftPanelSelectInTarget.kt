package com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel

import com.intellij.ide.SelectInContext
import com.intellij.ide.SelectInTarget
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId

internal class WelcomeScreenLeftPanelSelectInTarget : SelectInTarget {
  override fun isAvailable(project: Project): Boolean = false

  override fun canSelect(context: SelectInContext?): Boolean = false

  override fun selectIn(context: SelectInContext?, requestFocus: Boolean): Unit = Unit

  override fun getToolWindowId(): String = ToolWindowId.PROJECT_VIEW

  override fun getMinorViewId(): String = WelcomeScreenLeftPanel.Companion.ID
}