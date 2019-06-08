// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community

import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.FirstStart
import com.intellij.testGuiFramework.impl.FirstStart.Utils.button
import com.intellij.testGuiFramework.impl.FirstStart.Utils.dialog
import com.intellij.testGuiFramework.launcher.GuiTestLocalLauncher
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.launcher.ide.Ide
import com.intellij.testGuiFramework.remote.IdeControl
import com.intellij.testGuiFramework.util.ScreenshotTaker
import com.intellij.ui.components.labels.ActionLink
import org.fest.swing.core.Robot
import org.junit.Test
import java.awt.Point
import java.net.URL
import javax.swing.JLabel
import javax.swing.text.Document
import javax.swing.text.JTextComponent

class IdeaUpdateGuiTest {

  private val customUpdatesXml: URL by lazy {
    IdeaUpdateGuiTest::class.java.getResource("/update/update.xml")
  }

  @Test
  fun testIdeaUpdate() {
    GuiTestLocalLauncher.firstStartIdeLocally(Ide(CommunityIde(), 0, 0), CommunityUpdateFirstStart::class.java.name,
                                              listOf(Pair("idea.updates.url", customUpdatesXml.toString())))
  }
}

class CommunityUpdateFirstStart : FirstStart(CommunityIde()) {
  override fun completeFirstStart() {
    "Complete Installation" { completeInstallation() }
    "Accept Agreement"      { acceptAgreement() }
    //    "Accept DataSharing"    { acceptDataSharing() }
    "Customize IDE"         { customizeIde() }
    //    "Evaluate License"      { evaluateLicense(ideType.name, myRobot) }
    "Find Welcome Frame"    { findWelcomeFrame() }
    "Test..."               { test() }
    "Close Welcome Frame"  { findWelcomeFrame()?.close() }
    "Wait until IDE is closed for 30 seconds" { Thread.sleep(Timeouts.seconds30.duration()) }
    "Close IDE with force " { IdeControl.closeIde() }
  }

  fun test() {
    try {
      val welcomeFrame = findWelcomeFrame()
      "Find action link with text \"Events\" and click it..." {
        val actionLink: JLabel = Utils.waitUntilFound(myRobot, welcomeFrame, JLabel::class.java,
                                                      Timeouts.seconds10) { it.javaClass.name == ActionLink::class.java.name && it.text == "Events" && it.isShowing }
        myRobot.click(actionLink)
      }
      "Find JTextComponent containing a text \"is ready to update\" and click \"update\" word" {
        val jEditorPane = Utils.waitUntilFound(myRobot, welcomeFrame, JTextComponent::class.java, Timeouts.minutes01) {
          it.document.contains("is ready to update")
        }
        jEditorPane.clickTextInJEditorPane(myRobot, "update")
      }
      "Find dialog with title \"IDE and Plugin Updates\"" {
        myRobot.dialog("IDE and Plugin Updates", Timeouts.minutes01)
      }
      "Click button \"Remind Me Later\"" {
        myRobot.button("Remind Me Later", Timeouts.seconds05).click()
      }
    }
    catch (e: Exception) {
      ScreenshotTaker.takeScreenshotAndHierarchy("TestStep")
      e.printStackTrace(System.err)
    }
  }

  private fun JTextComponent.clickTextInJEditorPane(robot: Robot, textToClick: String) {
    myRobot.click(this, this.getTextCenter(textToClick))
  }

  private fun Document.contains(text: String): Boolean = this.text.contains(text)

  private val Document.text: String
    get() {
      return this.getText(0, length)
    }

  private fun JTextComponent.getTextCenter(text: String): Point {
    val offset = document.text.indexOf(text)
    val coordinates = modelToView(offset + text.length / 2)
    return Point(coordinates.centerX.toInt(), coordinates.centerY.toInt())
  }
}


operator fun String.invoke(block: () -> Unit) {
  println("Step: $this")
  block()
}