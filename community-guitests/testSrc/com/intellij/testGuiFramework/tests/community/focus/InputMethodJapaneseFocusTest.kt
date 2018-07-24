// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil.textfield
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.framework.GuiTestUtil.defaultTimeout
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.util.Key.ESCAPE
import org.fest.swing.core.SmartWaitRobot
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.timing.Pause
import org.junit.Assert
import org.junit.Test
import java.awt.Container
import java.awt.Window
import javax.swing.JLabel

class InputMethodJapaneseFocusTest : GuiTestCase() {

  private val typedString = "hero"
  private val expectedText = "へろ"
  private val actionKeyStroke by lazy { ActionManager.getInstance().getKeyboardShortcut("GotoClass")!!.firstKeyStroke }

  /**
   * ATTENTION: RUN THIS TEST MANUALLY FROM MACOS ONLY WITH SELECTED *HIRAGANA* INPUT!
   *
   * This test checks the proper work for input methods on Japanese keyboard layout "Hiragana".
   * Steps:
   * 1. Create Command Line Project by template.
   * 2. Open in editor Main.java and navigate to offset 89.
   * 3. Invoke "Go to class" action and type hero. It should be converted to "へろ"
   */
  @Test
  fun testGoToClassFocus() {
    CommunityProjectCreator.importCommandLineAppAndOpenMain()
    Pause.pause(1000)
    ideFrame {
      focusOnEditor()
      searchTypeCheck(this@InputMethodJapaneseFocusTest, typedString, expectedText, checkTextInSearchWindow())
      focusOnEditor()
    }
  }

  private fun IdeFrameFixture.focusOnEditor() {
    editor { moveTo(89) }
  }

  private fun searchTypeCheck(guiTestCase: GuiTestCase,
                              stringToType: String,
                              expectedText: String,
                              checkTextFunction: GuiTestCase.(String) -> Unit) {

    val smartRobot = guiTestCase.robot() as SmartWaitRobot
    smartRobot.shortcutAndTypeString(actionKeyStroke, stringToType, 1000)
    Pause.pause(500)
    guiTestCase.checkTextFunction(expectedText)
    shortcut(ESCAPE)
  }

  private fun checkTextInSearchWindow(): GuiTestCase.(String) -> Unit = {
    val searchWindow = try {
      findSearchWindow()
    }
    catch (cle: ComponentLookupException) {
      this.robot().waitForIdle()
      findSearchWindow()
    }
    with(this) {
      val textfield = textfield("", searchWindow, defaultTimeout)
      Assert.assertEquals(textfield.target().text, it)
    }
  }

  private fun findSearchWindow(): Container {
    fun checkWindowContainsEnterClassName(it: Window) = GuiTestUtilKt.findAllWithBFS(it,
                                                                                     JLabel::class.java).firstOrNull { it.text == "Enter class name:" } != null
    return Window.getWindows()
             .filterNotNull()
             .firstOrNull { checkWindowContainsEnterClassName(it) } ?: throw ComponentLookupException(
      "Unable to find GoToClass search window")
  }

}