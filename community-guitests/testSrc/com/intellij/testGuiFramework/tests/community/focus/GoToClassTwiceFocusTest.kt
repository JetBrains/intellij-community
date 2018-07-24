// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.openapi.actionSystem.ShortcutSet
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.LogActionsDuringTest
import com.intellij.testGuiFramework.impl.ScreenshotsDuringTest
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.util.Key.ESCAPE
import org.fest.swing.core.SmartWaitRobot
import org.fest.swing.timing.Pause
import org.junit.Rule
import org.junit.Test
import java.lang.Math.tan
import java.util.*
import javax.swing.KeyStroke

class GoToClassTwiceFocusTest : GuiTestCase() {

  private val typedString = "hefuihwefwehrf;werfwerfw"

  @Rule @JvmField
  val screenshotsDuringTest = ScreenshotsDuringTest()
  @Rule @JvmField
  val logActionsDuringTest = LogActionsDuringTest()

  private val actionKeyStroke: KeyStroke by lazy {
    val activeKeymapShortcuts: ShortcutSet = KeymapUtil.getActiveKeymapShortcuts("GotoClass")
    KeymapUtil.getKeyStroke(activeKeymapShortcuts)!!
  }

  @Test
  fun testGoToClassFocusTwice() {
    CommunityProjectCreator.importCommandLineAppAndOpenMain()
    Pause.pause(1000)
    ideFrame {
      focusOnEditor()
      repeat(20) {
        intensiveCpuCalc()
        openGoToClassSearchAndType(this@GoToClassTwiceFocusTest)
        focusOnEditor()
      }
    }
  }

  private fun startIntensiveCalcOnEdt() {
    for (i in 0..1000) ApplicationManager.getApplication().invokeLater { println(intensiveCpuCalc().toString()) }
  }

  private fun startIntensiveCalcOnParallel() {
    ApplicationManager.getApplication().executeOnPooledThread {
      for (i in 0..100) ApplicationManager.getApplication().executeOnPooledThread {
        for (k in 0..1000) println(intensiveCpuCalc().toString())
      }
    }
  }

  private fun intensiveCpuCalc(): Double {
    val rand = Random()
    val salt = rand.nextDouble()
    fun f(a: Double): Double = tan(rand.nextDouble() * tan(rand.nextDouble() * tan(rand.nextDouble() * tan(a + 0.001))))
    fun g(n: Int, a: Double): Double {
      var res = a
      for (i in 0..n) res = f(res)
      return res
    }
    return g(10000, salt)
  }

  private fun IdeFrameFixture.focusOnEditor() {
    editor {
      moveTo(89)
    }
  }

  private fun openGoToClassSearchAndType(guiTestCase: GuiTestCase) {

    val smartRobot = guiTestCase.robot() as SmartWaitRobot
    smartRobot.shortcut(actionKeyStroke)
    smartRobot.shortcutAndTypeString(actionKeyStroke, typedString, 100)
    Pause.pause(500)
    FocusIssuesUtil.checkSearchEverywhereUI(typedString)
    shortcut(ESCAPE)
  }

}