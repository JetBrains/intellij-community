// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.util.Key.R
import com.intellij.testGuiFramework.util.Modifier.*
import com.intellij.testGuiFramework.util.plus
import org.fest.swing.timing.Pause
import org.junit.Test
import java.util.concurrent.TimeUnit

class ReplaceInPathFocusTest: GuiTestCase() {

  /**
   * Regression test to catch TypeAhead exception during the DumbMode and calling Replace in Path action.
   * IDEA-188519
   * Fixed by f5002ec244eec48c26be78ccbc6cbb1058581d50
   */
  @Test
  fun testReplaceInPathDumbModeFocus() {
    CommunityProjectCreator.createCommandLineProject()
    Pause.pause(1000)
    ideFrame {
      dumbMode {
        shortcut(CONTROL + SHIFT + R, META + SHIFT + R)
        Pause.pause(30, TimeUnit.SECONDS)
      }
    }
  }

  private fun IdeFrameFixture.dumbMode(blockInDumbMode: GuiTestCase.() -> Unit) {
    this@dumbMode.invokeMainMenu("DumbMode")
    blockInDumbMode()
    this@dumbMode.invokeMainMenu("DumbMode")
  }


}