// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.tests.community

import com.intellij.openapi.util.component1
import com.intellij.openapi.util.component2
import com.intellij.testGuiFramework.fixtures.IdeFrameFixture
import com.intellij.testGuiFramework.fixtures.JBListPopupFixture
import com.intellij.testGuiFramework.fixtures.JDialogFixture
import com.intellij.testGuiFramework.framework.RunWithIde
import com.intellij.testGuiFramework.impl.GuiTestCase
import com.intellij.testGuiFramework.impl.GuiTestUtilKt
import com.intellij.testGuiFramework.launcher.ide.CommunityIde
import com.intellij.testGuiFramework.util.Key
import com.intellij.ui.popup.PopupFactoryImpl
import org.fest.swing.exception.ComponentLookupException
import org.fest.swing.timing.Pause.pause
import org.fest.swing.timing.Timeout
import org.junit.Test
import java.util.concurrent.TimeUnit

@RunWithIde(CommunityIde::class)
class TypeAheadTest : GuiTestCase() {

  @Test
  fun testProjectCreate() {
    CommunityProjectCreator.createCommandLineProject("type-ahead-problem")
    ideFrame {
      //a pause to wait when an (Edit Configurations...) action will be enabled
      pause(5000)
      waitForBackgroundTasksToFinish()
      openRunDebugConfiguration()
      dialog("Run/Debug Configurations") {
        addJUnitConfiguration()
        for (i in 0..20) {
          combobox("Test kind:").selectItem("Category")
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

  private fun IdeFrameFixture.openRunDebugConfiguration() {
    val attempts = 5
    val timeoutInterval = 2L
    navigationBar {
      if (!isShowing()) show()
      actionButton("Run").waitUntilEnabledAndShowing()
      for (i in 0..attempts) {
        button("Main").click()
        if (ensureEditConfigurationsIsEnabled()) break
        else if (i == attempts - 1) throw Exception("Action 'Edit Configurations' is still disabled")
        pause(timeoutInterval, TimeUnit.SECONDS)
        shortcut(Key.ESCAPE)
      }
      popupClick("Edit Configurations...")
      //check that we clicked to edit configurations after it become enabled
      if (!ensureJBListPopupFixtureIsGone()) {
        shortcut(Key.ESCAPE)
        button("Main").click()
        popupClick("Edit Configurations...")
      }
    }
  }

  private fun IdeFrameFixture.ensureJBListPopupFixtureIsGone(timeoutInMs: Long = 100): Boolean {
    val (jListFixture, index) = try {
      getJBListPopupFixtureAndItem(timeoutInMs)
    }
    catch (cle: ComponentLookupException) {
      return true
    }
    return !jListFixture.target().isShowing
  }

  private fun IdeFrameFixture.ensureEditConfigurationsIsEnabled(): Boolean {
    val (jListFixture, index) = getJBListPopupFixtureAndItem()
    val actionItem = jListFixture.target().model.getElementAt(index) as PopupFactoryImpl.ActionItem
    return actionItem.action.templatePresentation.isEnabled
  }

  private fun IdeFrameFixture.getJBListPopupFixtureAndItem(timeoutInMs: Long = 1000) =
    JBListPopupFixture.getJListFixtureAndItemToClick("Edit Configurations...", false, null, this.robot(),
                                                     Timeout.timeout(timeoutInMs))

}