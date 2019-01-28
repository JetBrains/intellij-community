// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community

import com.intellij.openapi.application.ApplicationManager
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.framework.GuiTestUtil
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.waitUntil
import com.intellij.testGuiFramework.launcher.system.SystemInfo
import com.intellij.testGuiFramework.launcher.system.SystemInfo.isMac
import com.intellij.testGuiFramework.launcher.system.SystemInfo.isUnix
import com.intellij.testGuiFramework.launcher.system.SystemInfo.isWin
import com.intellij.testGuiFramework.util.Key.*
import com.intellij.testGuiFramework.util.Modifier.*
import com.intellij.testGuiFramework.util.plus
import org.fest.swing.exception.WaitTimedOutError
import org.fest.swing.timing.Timeout
import org.junit.Test
import java.io.File
import java.nio.file.Paths
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import kotlin.test.assertEquals

//test for issue (GUI-167)
class SaveFilesOnFrameDeactivationGuiTest : GuiTestCase() {

  private val textToWrite = """new text in editor"""

  @Test
  fun testSwitchBetweenIdeFrames() {

    ensureSaveFilesOnFrameDeactivation()

    val ideFrame1 = CommunityProjectCreator.importProject("command-line-app")
      .let { IdeFrameFixture.find(robot(), it, null, Timeouts.seconds10) }
    ideFrame1.prepare()

    val ideFrame2File = CommunityProjectCreator.importProject("command-line-app2")
    openProjectInNewWindow()
    val ideFrame2 = IdeFrameFixture.find(robot(), ideFrame2File, null, Timeouts.seconds10)
    ideFrame2.prepare()

    val filePath = getCurrentEditorFilePath(ideFrame2)
    ideFrame2.pasteTextToEditor(textToWrite)
    //modify ideFrame2 main file
    ideFrame1.switchFrameTo()
    //check ideFrame2 content
    val fileSystemText = waitFileToBeSynced(textToWrite, filePath)
    ideFrame1.closeProjectAndWaitWelcomeFrame(false)
    ideFrame2.closeProjectAndWaitWelcomeFrame(false)

    assertEquals(textToWrite, fileSystemText)
  }

  @Test
  fun testSwitchIdeFrameToApp() {

    val ideFrame = CommunityProjectCreator.importProject("command-line-app")
      .let { IdeFrameFixture.find(robot(), it, null, Timeouts.seconds10) }
    ideFrame.prepare()

    val filePath = getCurrentEditorFilePath(ideFrame)
    ideFrame.pasteTextToEditor(textToWrite)

    val processBuilder = dummyUiApp()
    val process = processBuilder.start()
    process.waitFor(3, TimeUnit.SECONDS)
    process.destroyForcibly()

    val fileSystemText = waitFileToBeSynced(textToWrite, filePath)
    ideFrame.closeProjectAndWaitWelcomeFrame(false)
    assertEquals(textToWrite, fileSystemText)
  }

  @Test
  fun testSwitchIdeFrameToAppBlockedByModal() {
    val ideFrame = CommunityProjectCreator.importProject("command-line-app")
      .let { IdeFrameFixture.find(robot(), it, null, Timeouts.seconds10) }
    ideFrame.prepare()

    val filePath = getCurrentEditorFilePath(ideFrame)
    val originalTextFromEditor = File(filePath).readText()
    ideFrame.pasteTextToEditor(textToWrite)
    openSettings()

    val processBuilder = dummyUiApp()
    val process = processBuilder.start()
    process.waitFor(3, TimeUnit.SECONDS)
    process.destroyForcibly()

    val fileSystemText = waitFileToBeSynced(textToWrite, filePath)
    dialog(if (SystemInfo.isMac()) "Preferences" else "Settings") {
      button("Cancel").click()
    }
    ideFrame.closeProjectAndWaitWelcomeFrame(false)
    assertEquals(originalTextFromEditor, fileSystemText)
  }

  private fun openSettings() {
    shortcut(CONTROL + ALT + S, META + COMMA)
  }

  private fun getCurrentEditorFilePath(ideFrame2: IdeFrameFixture) =
    ApplicationManager.getApplication().runReadAction<String> { ideFrame2.let { it.editor.currentFile!!.path } }

  private fun IdeFrameFixture.pasteTextToEditor(text: String) {
    editor {
      shortcut(CONTROL + A, META + A)
      typeText(text)
    }
    waitUntil("text in editor will match entered text", Timeouts.seconds05) { editor.getCurrentFileContents(false) == text }
  }

  private fun dummyUiApp(): ProcessBuilder {
    val dummyUIAppClassStr: String = DummyUIApp::class.java.name.replace(".", "/") + ".class"
    val cl = this.javaClass.classLoader.getResource(dummyUIAppClassStr)
    val classpath = File(cl.toURI()).path.dropLast(dummyUIAppClassStr.length)
    val javaPath = System.getProperty("java.home") ?: throw Exception("Unable to locate java")
    val javaFilePath = Paths.get(javaPath, "bin", "java").toFile().path
    return ProcessBuilder(javaFilePath, "-classpath", classpath, DummyUIApp::class.java.name).apply { inheritIO() }
  }

  private fun ensureSaveFilesOnFrameDeactivation() {
    val preferences = if (SystemInfo.isMac()) "Preferences" else "Settings"

    welcomeFrame {
      actionLink("Configure").click()
      popupMenu(preferences).clickSearchedItem()
      dialog("$preferences for New Projects") {
        jTree("Appearance & Behavior", "System Settings").clickPath()
        checkbox("Save files on frame deactivation").apply {
          if (!isSelected) click()
        }
        button("OK").click()
      }
    }
  }

  private fun waitFileToBeSynced(textInEditor: String, filePath: String?, timeout: Timeout = Timeouts.seconds30): String {
    var textFromFile: String = ""
    try {
      waitUntil("File content and editor's content will be synced", timeout) {
        textFromFile = File(filePath).readText()
        textInEditor == textFromFile
      }
    }
    catch (e: WaitTimedOutError) {
      System.err.println(e.message)
    }
    return textFromFile
  }

  private fun IdeFrameFixture.switchFrameTo() {
    if (this.target().isActive) return
    when {
      isWin() -> shortcut(CONTROL + ALT + OPEN_BRACKET)
      isMac() -> shortcut(META + BACK_QUOTE)
      isUnix() -> shortcut(ALT + BACK_QUOTE)
    }
    GuiTestUtilKt.waitUntil("IdeFrame[${this.projectPath}] will be activated", Timeouts.seconds02) { this.target().isActive }
  }

  private fun IdeFrameFixture.prepare() {
    this.apply {
      waitForStartingIndexing(10)
      waitForBackgroundTasksToFinish()
      projectView {
        path(project.name, "src", "com.company", "Main").doubleClick()
        waitForBackgroundTasksToFinish()
      }
    }
  }

  private fun GuiTestCase.openProjectInNewWindow() {
    val button = GuiTestUtil.waitUntilFound(this.robot(), null, GuiTestUtilKt.typeMatcher(JButton::class.java) { it.text == "New Window" })
    this.robot().click(button)
  }

}