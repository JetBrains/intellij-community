// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.impl.message
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.util.Key
import com.intellij.testGuiFramework.util.Key.R
import com.intellij.testGuiFramework.util.Modifier.*
import com.intellij.testGuiFramework.util.plus
import org.fest.swing.timing.Pause
import org.junit.Assert
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReplaceInPathFocusTest : GuiTestCase() {

   /**
   * Regression test to catch TypeAhead exception during the DumbMode and calling Replace in Path action.
   * IDEA-188519
   * Fixed by f5002ec244eec48c26be78ccbc6cbb1058581d50
   */
  @Test
  fun testReplaceInPathDumbModeFocus() {
    CommunityProjectCreator.importCommandLineAppAndOpenMain()
    Pause.pause(1000)
    ideFrame {
      dumbMode {
        replaceInPath()
        Pause.pause(30, TimeUnit.SECONDS)
      }
    }
  }

  /**
   * Regression test to catch TypeAhead exception during Replace in Path action.
   * IDEA-188229
   * Fixed by 6bd20c4a483a78713c08e426316d29d3cfd1f6a5
   */
  @Test
  fun testReplaceInPathFocus() {
    CommunityProjectCreator.importCommandLineAppAndOpenMain()
    Pause.pause(1000)
    ideFrame {
      replaceInPath()
      typeText("main")
      shortcut(Key.TAB)
      typeText("test")
      button("Replace All").click()
      message("Replace All").click("Replace")
      Pause.pause(1000)
      val editorCode = editor.getCurrentFileContents(false) ?: throw Exception("Unable to get editor's content")
      Assert.assertTrue("Editor shouldn't contain 'main' after replacement in path: Editor's content \n $editorCode", !editorCode.contains("main"))
    }
  }

  private fun replaceInPath() {
    shortcut(CONTROL + SHIFT + R, META + SHIFT + R)
  }

  private fun IdeFrameFixture.dumbMode(blockInDumbMode: GuiTestCase.() -> Unit) {
    this@dumbMode.invokeMainMenu("DumbMode")
    blockInDumbMode()
    this@dumbMode.invokeMainMenu("DumbMode")
  }


}