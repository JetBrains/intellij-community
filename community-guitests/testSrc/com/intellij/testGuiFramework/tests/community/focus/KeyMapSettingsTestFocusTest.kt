// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.openapi.keymap.impl.ui.ShortcutTextField
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.launcher.system.SystemInfo
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.util.Key.*
import com.intellij.testGuiFramework.util.Modifier.*
import com.intellij.testGuiFramework.util.plus
import org.fest.swing.fixture.JTextComponentFixture
import org.fest.swing.timing.Pause
import org.junit.Assert
import org.junit.Test
import java.awt.Container


class KeyMapSettingsTestFocusTest : GuiTestCase() {

  @Test
  fun testKeyMapSettingsTestFocus() {
    CommunityProjectCreator.importCommandLineAppAndOpenMain()
    Pause.pause(1000)
    ideFrame {
      waitForBackgroundTasksToFinish()
      openSettingsKeymap()
    }

  }

  private fun IdeFrameFixture.openSettingsKeymap() {
    shortcut(CONTROL + ALT + S, META + COMMA)
    val settingsTitle = if (SystemInfo.isMac()) "Preferences" else "Settings"
    dialog(settingsTitle){
      jTree("Keymap").clickPath("Keymap")
      actionButton("Find Actions by Shortcut").click()
      val keyboardShortcutPanel = checkbox("Second stroke").target().parent
      val keyMapTextField = shortcutTextField(keyboardShortcutPanel)
      keyMapTextField.click()
      shortcut(CONTROL + N)
      Pause.pause(500)
      shortcut(B)
      Assert.assertEquals("B", keyMapTextField.target().text)
      shortcut(ESCAPE)
      shortcut(ESCAPE) //close keymap popup
      Pause.pause(500)
      button("Cancel").click()
    }
  }

  private fun IdeFrameFixture.shortcutTextField(parentContainer: Container): JTextComponentFixture {
    val robot = robot()
    val textField = robot.finder().find(parentContainer) { it is ShortcutTextField && it.isShowing && it.isVisible && it.isEnabled } as ShortcutTextField
    return JTextComponentFixture(robot, textField)
  }

}