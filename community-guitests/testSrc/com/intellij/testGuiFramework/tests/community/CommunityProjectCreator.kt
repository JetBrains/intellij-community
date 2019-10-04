// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community

import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.newclass.CreateWithTemplatesDialogPanel
import com.intellij.openapi.diagnostic.Logger
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitProgressDialogUntilGone
import com.intellij.testGuiFramework.util.Key.*
import com.intellij.testGuiFramework.util.Modifier.CONTROL
import com.intellij.testGuiFramework.util.Modifier.META
import com.intellij.testGuiFramework.util.plus
import com.intellij.testGuiFramework.util.step
import com.intellij.testGuiFramework.utils.TestUtilsClass
import com.intellij.testGuiFramework.utils.TestUtilsClassCompanion
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.exception.WaitTimedOutError
import org.junit.Assert
import java.nio.file.Path
import java.nio.file.Paths

val GuiTestCase.CommunityProjectCreator by CommunityProjectCreator

class CommunityProjectCreator(guiTestCase: GuiTestCase) : TestUtilsClass(guiTestCase) {

  companion object : TestUtilsClassCompanion<CommunityProjectCreator>({ it -> CommunityProjectCreator(it) })

  private val LOG = Logger.getInstance(this.javaClass)

  private val defaultProjectName = "untitled"

  fun createCommandLineProject(projectName: String = defaultProjectName, needToOpenMainJava: Boolean = true) {
    with(guiTestCase) {
      welcomeFrame {
        actionLink("Create New Project").click()
        waitProgressDialogUntilGone(robot = robot(), progressTitle = "Loading Templates", timeoutToAppear = Timeouts.seconds02)
        dialog("New Project") {
          jList("Java").clickItem("Java")
          button("Next").click()
          val projectFromTemplateCheckbox = checkbox("Create project from template")
          projectFromTemplateCheckbox.click()
          //check that "Create project from template" has been clicked. GUI-80
          if (!projectFromTemplateCheckbox.isSelected) projectFromTemplateCheckbox.click()
          Assert.assertTrue("Checkbox \"Create project from template\" should be selected!", projectFromTemplateCheckbox.isSelected)
          jList("Command Line App").clickItem("Command Line App")
          button("Next").click()
          typeText(projectName)
          checkFileAlreadyExistsDialog() //confirm overwriting already created project
          button("Finish").click()
        }
      }
      waitForFirstIndexing()
      if (needToOpenMainJava) openMainInCommandLineProject()
    }
  }

  private fun GuiTestCase.checkFileAlreadyExistsDialog() {
    try {
      val dialogFixture = dialog(IdeBundle.message("title.file.already.exists"), false, timeout = Timeouts.seconds01)
      dialogFixture.button("Yes").click()
    }
    catch (cle: ComponentLookupException) { /*do nothing here */
    }
  }

  fun GuiTestCase.openMainInCommandLineProject() {
    ideFrame {
      projectView {
        path(project.name, "src", "com.company", "Main").doubleClick()
        waitForBackgroundTasksToFinish()
      }
    }
  }

  fun GuiTestCase.openFileInCommandLineProject(fileName: String) {
    ideFrame {
      projectView {
        path(project.name, "src", "com.company", fileName).doubleClick()
        waitForBackgroundTasksToFinish()
      }
    }
  }

  fun GuiTestCase.waitForFirstIndexing() {
    ideFrame {
      val secondToWaitIndexing = 10
      try {
        waitForStartingIndexing(secondToWaitIndexing) //let's wait for 2 minutes until indexing bar will appeared
      }
      catch (timedOutError: WaitTimedOutError) {
        LOG.warn("Waiting for indexing has been exceeded $secondToWaitIndexing seconds")
      }
      waitForBackgroundTasksToFinish()
    }
  }

  fun createJavaClass(fileContent: String, fileName: String = "Test") {
    with(guiTestCase) {
      ideFrame {
        projectView {
          path(project.name, "src", "com.company").rightClick()
        }
        menu("New", "Java Class").click()
        step("Find CreateWithTemplatesDialogPanel to detect popup for a new class") {
          findComponentWithTimeout(null, CreateWithTemplatesDialogPanel::class.java,
                                   Timeouts.seconds02)
          typeText(fileName)
          shortcut(ENTER)
        }
        editor("$fileName.java") {
          shortcut(CONTROL + A, META + A)
          copyToClipboard(fileContent)
          shortcut(CONTROL + V, META + V)
        }
      }
    }
  }

  /**
   * @projectName of importing project should be locate in the current module testData/
   */
  fun importProject(projectName: String): Path {
    val projectDirUrl = this.javaClass.classLoader.getResource(projectName)
    return guiTestCase.guiTestRule.importProject(Paths.get(projectDirUrl.toURI()))
  }

  fun importCommandLineApp() {
    importProject("command-line-app")
  }

  /**
   * @fileName - name of file with extension stored in src/com.company/
   */
  fun importCommandLineAppAndOpenFile(fileName: String) {
    importCommandLineApp()
    guiTestCase.waitForFirstIndexing()
    guiTestCase.openFileInCommandLineProject(fileName)
  }

  fun importCommandLineAppAndOpenMain() {
    importCommandLineApp()
    guiTestCase.waitForFirstIndexing()
    guiTestCase.openMainInCommandLineProject()
  }

}

