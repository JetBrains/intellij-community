// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.toolWindow

import com.intellij.openapi.actionSystem.impl.ActionMenuItem
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import org.fest.swing.timing.Pause
import org.junit.After
import org.junit.Assert
import org.junit.Test

class DockedModeGuiTest : GuiTestCase() {

  enum class ToolWindowModes(val mode: String) {
    PINNED_MODE("Pinned Mode"),
    DOCKED_MODE("Docked Mode")
  }

  @Test
  fun testDockedMode() {
    CommunityProjectCreator.importCommandLineAppAndOpenMain()
    ideFrame {
      if (!projectView.isVisible) projectView.activate()
      setProjectViewMode(ToolWindowModes.PINNED_MODE, true)
      editor.clickCenter()
      setProjectViewMode(ToolWindowModes.DOCKED_MODE, false)
      editor.clickCenter()
      Pause.pause(2000) // pause to wait when project view is closed
      val projectViewWasVisible = projectView.isVisible
      Assert.assertFalse("Project tool window should be hidden in 'pinned' and not 'docked' mode", projectViewWasVisible)
    }
  }

  @After
  fun tearDown() {
    ideFrame { setProjectViewMode(ToolWindowModes.DOCKED_MODE, true) }
  }

  private fun IdeFrameFixture.setProjectViewMode(toolWindowMode: ToolWindowModes, flag: Boolean) {
    if (!projectView.isVisible) {
      projectView.activate()
      Pause.pause(2000) // pause to wait when project view is appeared
    }
    actionButton("Show Options Menu").click()
    val pinnedModeItem = menu(toolWindowMode.mode)
    val selected = (pinnedModeItem.target() as ActionMenuItem).isSelected
    if (selected != flag) pinnedModeItem.click()
  }

}