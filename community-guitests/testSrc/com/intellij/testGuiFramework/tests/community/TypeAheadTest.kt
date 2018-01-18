// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community

import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import org.fest.swing.timing.Pause
import org.junit.Test

@RunWithIde(CommunityIde::class)
class TypeAheadTest : GuiTestCase() {

  @Test
  fun testProjectCreate() {
    createProject()
    ideFrame {
      //a pause to wait when an (Edit Configurations...) action will be enabled
      Pause.pause(5000)
      waitForBackgroundTasksToFinish()
      openRunDebugConfiguration()
      dialog("Run/Debug Configurations") {
        addJUnitConfiguration()
        for (i in 0..20) {
          combobox("Test kind:").selectItem("Category")
          Pause.pause(2000)
          combobox("Test kind:").selectItem("Class")
          Pause.pause(2000)
          combobox("Test kind:").selectItem("Method")
          Pause.pause(2000)
        }
      }
      Pause.pause(30000)
    }
  }


  private fun JDialogFixture.addJUnitConfiguration() {
    val actionName = "Add New Configuration"
    GuiTestUtilKt.waitUntil("action button will be visible") { actionButton(actionName).target().isShowing }
    actionButton(actionName).click()
    popupClick("JUnit")
  }

  private fun GuiTestCase.createProject() {
    welcomeFrame {
      actionLink("Create New Project").click()
      dialog("New Project") {
        button("Next").click()
        checkbox("Create project from template").click()
        jList("Command Line App").clickItem("Command Line App")
        button("Next").click()
        typeText("typeAheadProblem")
        button("Finish").click()
      }
    }
  }

  private fun IdeFrameFixture.openRunDebugConfiguration() {
    navigationBar {
      if (!isShowing()) show()
      actionButton("Run").waitUntilEnabledAndShowing()
      button("Main").click()
      popupClick("Edit Configurations...")
    }
  }

}