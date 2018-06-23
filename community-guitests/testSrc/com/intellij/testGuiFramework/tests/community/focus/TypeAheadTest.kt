// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.tests.community.CommunityProjectCreator
import org.fest.swing.timing.Pause.pause
import org.junit.Test

@RunWithIde(CommunityIde::class)
class TypeAheadTest : GuiTestCase() {

  @Test
  fun testTypeAhead() {
    CommunityProjectCreator.importCommandLineAppAndOpenMain()
    ideFrame {
      waitForBackgroundTasksToFinish()
      configurationList {
        editConfigurations()
      }
      dialog("Run/Debug Configurations") {
        addJUnitConfiguration()
        for (i in 0..20) {
          combobox("Test kind:").selectItem("Pattern")
          pause(2000)
          combobox("Test kind:").selectItem("Class")
          pause(2000)
          combobox("Test kind:").selectItem("Method")
          pause(2000)
        }
        button("OK").click()
      }
      pause(30000)
    }
  }


  private fun JDialogFixture.addJUnitConfiguration() {
    val actionName = "Add New Configuration"
    GuiTestUtilKt.waitUntil("action button will be visible") { actionButton(actionName).target().isShowing }
    actionButton(actionName).click()
    popupClick("JUnit")
  }
}