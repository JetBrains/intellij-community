// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community

import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion

val GuiTestCase.CommunityProjectCreator by CommunityProjectCreator

class CommunityProjectCreator(guiTestCase: GuiTestCase) : TestUtilsClass(guiTestCase) {

  companion object : TestUtilsClassCompanion<CommunityProjectCreator>({ it -> CommunityProjectCreator(it) })

  private val defaultProjectName = "untitled"

  fun createCommandLineProject(projectName: String = defaultProjectName, needToOpenMainJava: Boolean = true) {
    with(guiTestCase) {
      welcomeFrame {
        actionLink("Create New Project").click()
        GuiTestUtilKt.waitProgressDialogUntilGone(robot(), "Loading Templates")
        dialog("New Project") {
          button("Next").click()
          checkbox("Create project from template").click()
          jList("Command Line App").clickItem("Command Line App")
          button("Next").click()
          if (projectName != defaultProjectName) typeText("typeAheadProblem")
          button("Finish").click()
        }
      }
      ideFrame {
        waitForBackgroundTasksToFinish()
        if (needToOpenMainJava) {
          projectView {
            path(project.name, "src", "com.company", "Main").doubleClick()
          }
        }
      }
    }
  }

}

