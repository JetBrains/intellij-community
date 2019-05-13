// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.util.Key
import com.intellij.testGuiFramework.util.Modifier
import com.intellij.testGuiFramework.util.plus
import org.fest.swing.core.SmartWaitRobot
import org.fest.swing.timing.Pause
import org.junit.Assert
import org.junit.Test

class CompletionFocusTest: GuiTestCase() {

  @Test
  fun testLostSymbolsByCompletion() {
    val testString = "mxmmxmxmmxmxmmxmxmxmmxmx"
    CommunityProjectCreator.importCommandLineAppAndOpenFile("A.cpp")
    Pause.pause(1000)
    val fastRobot = robot() as SmartWaitRobot
    ideFrame {
      repeat(10) {
        fastRobot.fastTyping("mx", delayBetweenShortcutAndTypingMs = 20)
        fastRobot.fastTyping(testString, delayBetweenShortcutAndTypingMs = 20)
        Pause.pause(1000)
        Assert.assertEquals("mx" + testString, editor.getCurrentFileContents(false))
        editor {
          //select All
          shortcut(Modifier.CONTROL + Key.A, Modifier.META + Key.A)
          //delete
          shortcut(Key.BACK_SPACE)
        }
      }
    }
  }

}