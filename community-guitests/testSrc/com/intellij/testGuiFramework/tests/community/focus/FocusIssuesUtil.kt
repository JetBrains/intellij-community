// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community.focus

import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI
import com.intellij.testGuiFramework.impl.GuiRobotHolder
import com.intellij.ui.components.fields.ExtendableTextField
import org.fest.swing.exception.ComponentLookupException
import org.junit.Assert
import java.awt.Container

object FocusIssuesUtil {

  fun checkSearchEverywhereUI(expectedString: String) {
    val searchEverywhereUI = try {
      findSearchEverywhereUI()
    }
    catch (cle: ComponentLookupException) {
      GuiRobotHolder.robot.waitForIdle()
      findSearchEverywhereUI()
    }
    val extendableTextField: ExtendableTextField =  GuiRobotHolder.robot.finder().find(searchEverywhereUI) { it is ExtendableTextField } as ExtendableTextField
    Assert.assertEquals(expectedString, extendableTextField.text)
  }

  private fun findSearchEverywhereUI(): Container {
    return GuiRobotHolder.robot.finder().find { it is SearchEverywhereUI } as SearchEverywhereUI
  }
}