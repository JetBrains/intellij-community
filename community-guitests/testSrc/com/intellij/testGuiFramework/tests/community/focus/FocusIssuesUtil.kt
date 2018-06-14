// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.testGuiFramework.framework.GuiTestUtil.defaultTimeout
import com.intellij.testGuiFramework.framework.GuiTestUtil.textfield
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.ui.components.fields.ExtendableTextField
import org.fest.swing.exception.ComponentLookupException
import org.junit.Assert
import java.awt.Container
import java.awt.Window
import javax.swing.JLabel

object FocusIssuesUtil {

  fun checkSearchEnteredText(expectedString: String) {
    val actualString = try {
      val searchEverywhereUI = findSearchUiWithWaitingIdle()
      val extendableTextField: ExtendableTextField = GuiRobotHolder.robot.finder().find(
        searchEverywhereUI) { it is ExtendableTextField } as ExtendableTextField
      extendableTextField.text
    } catch (cle: ComponentLookupException) {
      //if search dialog appeared as a HeavyWeightWindow
      textfield("", findSearchEverywhereWindowWithWaitingIdle(), defaultTimeout).text()
    }
    Assert.assertEquals(expectedString, actualString)
  }

  private fun findSearchEverywhereWindowWithWaitingIdle(): Container {
    return try {
      findSearchEverywhereWindow()
    }
    catch (cle: ComponentLookupException) {
      GuiRobotHolder.robot.waitForIdle()
      findSearchEverywhereWindow()
    }
  }

  private fun findSearchEverywhereWindow(): Container {
    fun checkWindowContainsEnterClassName(it: Window)
      = GuiTestUtilKt.findAllWithBFS(it, JLabel::class.java)
      .firstOrNull { it.text?.contains("Search Everywhere:") == true || it.text?.contains("Enter class name:") == true}  != null
    return Window.getWindows()
             .filterNotNull()
             .firstOrNull { checkWindowContainsEnterClassName(it) } ?: throw ComponentLookupException(
      "Unable to find search window")
  }

  private fun findSearchUiWithWaitingIdle(): Container {
    return try {
      findSearchEverywhereUI()
    } catch (cle: ComponentLookupException) {
      GuiRobotHolder.robot.waitForIdle()
      findSearchEverywhereUI()
    }
  }

  private fun findSearchEverywhereUI(): Container {
    return GuiRobotHolder.robot.finder().find { it is SearchEverywhereUI } as SearchEverywhereUI
  }

}