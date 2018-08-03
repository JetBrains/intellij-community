// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community

import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.impl.GuiTestUtilKt.typeMatcher
import com.intellij.testGuiFramework.impl.actionLink
import com.intellij.testGuiFramework.impl.button
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.testCases.SystemPropertiesTestCase
import org.junit.Test
import java.awt.Point
import java.net.URL
import javax.swing.JEditorPane
import javax.swing.text.Document


@RunWithIde(CommunityIde::class)
class IdeaUpdateGuiTest : SystemPropertiesTestCase() {

  private val customUpdatesXml: URL by lazy {
    IdeaUpdateGuiTest::class.java.getResource("/update/update.xml")
  }

  @Test
  fun testIdeaUpdate() {
    restartIdeWithSystemProperties(arrayOf(Pair("idea.updates.url", customUpdatesXml.toString())))
    welcomeFrame { actionLink("Events", timeout = 60).click() }
    clickTextInJEditorPane()
    dialog("IDE and Plugin Updates") { button("Remind Me Later").click() }
  }

  private fun clickTextInJEditorPane() {
    welcomeFrame {
      val jEditorPane: JEditorPane =  robot().finder().find(this.target(), typeMatcher(JEditorPane::class.java) {
        it.document.contains("is ready to update")
      })
      robot().click(jEditorPane, jEditorPane.getTextCenter("update"))
    }
  }

  private fun Document.contains(text: String): Boolean = this.text.contains(text)

  private val Document.text: String
    get() { return this.getText(0, length) }

  private fun JEditorPane.getTextCenter(text: String): Point {
    val offset = document.text.indexOf(text)
    val coordinates = modelToView(offset + text.length / 2)
    return Point(coordinates.centerX.toInt(), coordinates.centerY.toInt())
  }

}