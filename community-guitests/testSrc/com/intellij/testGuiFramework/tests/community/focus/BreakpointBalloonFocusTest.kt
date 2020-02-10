// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.testGuiFramework.fixtures.FileEditorFixture
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.util.Key
import org.fest.swing.core.SmartWaitRobot
import org.fest.swing.timing.Pause
import org.junit.Test
import java.awt.Component
import java.awt.Container
import javax.swing.FocusManager
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class BreakpointBalloonFocusTest : GuiTestCase() {

  private val NUMBER_OF_ELEMENTS_IN_BALLOON = 6

  @Test
  fun testFocusBreakpoint() {
    CommunityProjectCreator.importCommandLineAppAndOpenMain()
    Pause.pause(1000)
    ideFrame {
      editor {
        prepareEditor()
        openDebuggerBalloon()
        assertDebuggerBalloonInFocus()
        closeDebuggerBalloon()
        assertEditorInFocus()
      }
    }
  }

  private fun openDebuggerBalloon() {
    shortcut("control f8")
    shortcut("control shift f8")
  }

  private fun closeDebuggerBalloon() {
    shortcut("control ENTER")
  }

  private fun FileEditorFixture.prepareEditor() {
    val fastRobot = robot() as SmartWaitRobot
    moveTo(89)
    fastRobot.fastTyping("int i = 0;")
  }

  private fun assertDebuggerBalloonInFocus() {
    with(FocusManager.getCurrentManager()) {
      val originalFocusCycleRoot = currentFocusCycleRoot
      var prevFocusOwner = focusOwner

      // round trip over all the elements in the debugger balloon and go back to the condition field
      repeat(NUMBER_OF_ELEMENTS_IN_BALLOON) {
        shortcut(Key.TAB)

        assertTabMovesFocus(prevFocusOwner)
        assertFocusCycleRootSame(originalFocusCycleRoot)

        prevFocusOwner = focusOwner
      }
    }

    val fastRobot = robot() as SmartWaitRobot
    fastRobot.fastTyping("true");
  }

  private fun FocusManager.assertFocusCycleRootSame(balloon: Container?) {
    assertEquals(
      currentFocusCycleRoot,
      balloon,
      "The debugger balloon cannot lose focus while traversing it"
    )
  }

  private fun FocusManager.assertTabMovesFocus(prevFocusOwner: Component?) {
    assertNotEquals(
      prevFocusOwner,
      focusOwner,
      "TAB should move focus to a different element in the debugger balloon"
    )
  }

  private fun FileEditorFixture.assertEditorInFocus() {
    with(FocusManager.getCurrentManager()) {
      with (editor) {
        assertEquals(
          focusOwner,
          contentComponent,
          "The editor has to grab focus after the debugger balloon disappears"
        )
      }
    }
  }
}
