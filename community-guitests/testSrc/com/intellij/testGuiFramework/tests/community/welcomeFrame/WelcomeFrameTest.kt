/*
 * Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package com.intellij.testGuiFramework.tests.community.welcomeFrame

import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.framework.Timeouts.seconds01
import com.intellij.testGuiFramework.framework.wait
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.actionLink
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import com.intellij.testGuiFramework.util.Key
import org.junit.Test

@RunWithIde(CommunityIde::class)
class WelcomeFrameTest(private val testCounter: TestCounterParameters): GuiTestCase() {

  @Test
  fun testConfigureLinkAndImportProject() {
    clickConfigureOnWelcomeFrame()
    CommunityProjectCreator.importCommandLineAppAndOpenMain()
  }

  private fun clickConfigureOnWelcomeFrame() {
    welcomeFrame {
      actionLink("Configure").click()
      seconds01.wait()
      shortcut(Key.ESCAPE)
    }
  }
}