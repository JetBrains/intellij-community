// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.ComparisonUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.ex.EditorSettingsExternalizable
import com.intellij.openapi.ui.MultiLineLabelUI
import com.intellij.openapi.util.text.StringUtil
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.impl.checkbox
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.util.Key.*
import com.intellij.testGuiFramework.util.Modifier.*
import com.intellij.testGuiFramework.util.plus
import org.fest.swing.core.SmartWaitRobot
import org.fest.swing.timing.Pause
import org.junit.Assert
import org.junit.Test
import java.lang.Math.tan
import java.util.*


class InlineVariableFocusTest : GuiTestCase() {

  private val pasteCode = """package com.company;

public class Test {

    public static void main(String[] args) {
        int a = 1;
        System.err.println(a);
    }
}"""

  private val destinationCode = """package com.company;

public class Test {

    public static void main(String[] args) {
        System.out.println(111);
    }
}"""

  @Test
  fun testInlineVariableFocus() {
    CommunityProjectCreator.importCommandLineAppAndOpenMain()
    Pause.pause(1000)
    CommunityProjectCreator.createJavaClass(pasteCode, "Test")
    ideFrame {
      waitForBackgroundTasksToFinish()
      val editorSettings = EditorSettingsExternalizable.getInstance()
      editorSettings.isShowInlineLocalDialog = false
      inlineVariable(firstTime = false)
      val smartRobot = robot() as SmartWaitRobot
      smartRobot.fastTyping("11", 0)
      for(i in 0 .. 10) shortcut(LEFT)
      for(i in 0 .. 2) shortcut(BACK_SPACE)
      smartRobot.fastTyping("out", 0)

      val editorCode = editor.getCurrentFileContents(false)
      Assert.assertTrue(ComparisonUtil.isEquals(editorCode!!.unifyCode(),
                                                destinationCode.unifyCode(),
                                                ComparisonPolicy.TRIM_WHITESPACES))
    }

  }

  private fun IdeFrameFixture.resetCode() {
    editor {
      shortcut(CONTROL + A, META + A)
      copyToClipboard(pasteCode)
      shortcut(CONTROL + V, META + V)
      moveTo(134)
    }
  }

  private fun IdeFrameFixture.inlineVariable(firstTime: Boolean) {
    editor {
      moveTo(134)
      shortcut(CONTROL + ALT + N,META + ALT + N)
      if (firstTime) {
        dialog("Inline Variable") {
          checkbox("Do not show this dialog in the future").click()
          button("Refactor").click()
        }
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

  fun String.unifyCode(): String =
    MultiLineLabelUI.convertTabs(StringUtil.convertLineSeparators(this), 2)


}