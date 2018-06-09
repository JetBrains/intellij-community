// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.util.Key.ESCAPE
import org.fest.swing.core.SmartWaitRobot
import org.fest.swing.timing.Pause
import org.junit.Test
import java.awt.event.InputEvent

class SearchEverywhereFocusTest : GuiTestCase() {

  private val typedString = "here is a demo text"

  @Test
  fun testSearchEverywhereFocus() {
    CommunityProjectCreator.importCommandLineAppAndOpenMain()
    Pause.pause(1000)
    ideFrame {
      focusOnEditor()
      doubleShift(50)
      fastType(typedString)
      Pause.pause(500)
      FocusIssuesUtil.checkSearchEverywhereUI(typedString)
      shortcut(ESCAPE)
      focusOnEditor()
    }
  }


  private fun GuiTestCase.doubleShift(delayBetweenMs: Int = 0) {
    val smartRobot = robot() as SmartWaitRobot
    smartRobot.fastPressAndReleaseModifiers(InputEvent.SHIFT_MASK)
    if (delayBetweenMs > 0) Pause.pause(delayBetweenMs.toLong())
    smartRobot.fastPressAndReleaseModifiers(InputEvent.SHIFT_MASK)
  }

  private fun IdeFrameFixture.focusOnEditor() { editor { moveTo(89) } }

  private fun GuiTestCase.fastType(stringToType: String) {
    val smartRobot = this@fastType.robot() as SmartWaitRobot
    smartRobot.fastTyping(stringToType)
  }



}