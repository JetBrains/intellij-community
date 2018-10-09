// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.completionPopup

import com.intellij.testGuiFramework.framework.dtrace.GuiDTTestCase
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.util.Key
import com.intellij.testGuiFramework.util.Modifier
import com.intellij.testGuiFramework.util.plus
import org.fest.swing.timing.Pause
import org.junit.Assert
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import java.io.InputStream
import java.util.concurrent.TimeUnit

class PopupWindowLeakTest : GuiDTTestCase() {
  companion object {
    @JvmStatic
    val MAX_ITERATION = 100
  }

  override fun getTestSrcPath(): String {
    return "${super.getTestSrcPath()}/testSrc/com/intellij/testGuiFramework/tests/community/completionPopup"
  }

  override fun getDTScriptName(): String {
    return "popup_window_leak.d"
  }

  //run on server side
  override fun checkDtraceLog(inStream: InputStream) {
    var initWithPlatformWindowCount = 0
    val lineList = mutableListOf<String>()
    inStream.bufferedReader().useLines { lines -> lines.forEach { lineList.add(it) } }
    lineList.forEach {
      println(it)
      if (it.contains("-initWithPlatformWindow")) {
        ++initWithPlatformWindowCount
      }
      else if (it.contains("-dealloc")) {
        --initWithPlatformWindowCount
      }
      assertTrue(
        "AWTWindow was not timely deallocated: initWithPlatformWindowCount=$initWithPlatformWindowCount",
        initWithPlatformWindowCount <= MAX_ITERATION / 2)
    }
  }

  @Rule
  @JvmField
  val myTimeoutRule = Timeout(10, TimeUnit.MINUTES)

  @Test
  fun testCompletionPopupHang() {

    CommunityProjectCreator.importCommandLineAppAndOpenMain()
    Pause.pause(1000)
    ideFrame {
      editor {
        moveTo(22)
        shortcut(Modifier.META + Key.A)
        typeText("import java.awt.*;\n" +
                 "public class Main {\n" +
                 "public static void main(String[] args) throws AWTException {\n" +
                 "Robot robot = new Robot();\n"
        )
        typeText("robot")
        var i = 0
        //Thread.sleep(300000)
        while (i < MAX_ITERATION) {
          typeText(".")
          Pause.pause(500)
          shortcut(Key.ESCAPE)
          Pause.pause(500)
          shortcut(Key.BACK_SPACE)
          Pause.pause(500)
          i++
        }
        Assert.assertEquals(MAX_ITERATION, i)
      }
    }
  }
}