// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.tests.community.focus.FocusIssuesUtil.checkSearchEverywhereUI
import com.intellij.testGuiFramework.util.Key.ESCAPE
import org.fest.swing.core.SmartWaitRobot
import org.fest.swing.timing.Pause
import org.junit.Test
import java.lang.Math.tan
import java.util.*

class GoToClassFocusTest : GuiTestCase() {

  private val typedString = "hefuihwefwehrf;werfwerfw"
  private val actionKeyStroke by lazy { ActionManager.getInstance().getKeyboardShortcut("GotoClass")!!.firstKeyStroke }

  @Test
  fun testGoToClassFocus() {
    CommunityProjectCreator.importCommandLineAppAndOpenMain()
    Pause.pause(1000)
    ideFrame {
      focusOnEditor()
      for (i in 0..10) {
        openGoToClassSearchAndType()
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

  private fun openGoToClassSearchAndType() {

    val smartRobot = GuiRobotHolder.robot as SmartWaitRobot
    smartRobot.shortcutAndTypeString(actionKeyStroke, typedString, 100)
    Pause.pause(500)
    checkSearchEverywhereUI(typedString)
    shortcut(ESCAPE)
  }



}