// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.installKotlinPlugin

import com.intellij.ide.projectWizard.kotlin.model.KotlinGuiTestCase
import com.intellij.ide.projectWizard.kotlin.model.KotlinTestProperties
import com.intellij.testGuiFramework.util.logTestStep
import com.intellij.testGuiFramework.util.logUIStep
import org.junit.Test

class CreateSdksGuiTest : KotlinGuiTestCase() {
  @Test
  fun createJdk(){
    logTestStep("Create a JDK on the path `${KotlinTestProperties.jdk_path}`")
    welcomeFrame {
      actionLink("Configure").click()
      popupClick("Project Defaults")
      popupClick("Project Structure")
      logUIStep("Open `Default Project Structure` dialog")
      dialog("Default Project Structure") {
        jList("SDKs").clickItem("SDKs")
        actionButton("Add New SDK").click()
        popupClick("JDK")
        logUIStep("Open `Select Home Directory for JDK` dialog")
        dialog("Select Home Directory for JDK") {
          actionButton("Refresh").click()
          logUIStep("Type the path `${KotlinTestProperties.jdk_path}`")
          typeText(KotlinTestProperties.jdk_path)
          logUIStep("Close `Select Home Directory for JDK` dialog with OK")
          button("OK").click()
        }
        logUIStep("Close `Default Project Structure` dialog with OK")
        button("OK").click()
      }
    }
  }

  @Test
  fun createKotlinSdk(){
    logTestStep("Create a Kotlin SDK")
    welcomeFrame {
      actionLink("Configure").click()
      popupClick("Project Defaults")
      popupClick("Project Structure")
      logUIStep("Open `Default Project Structure` dialog")
      dialog("Default Project Structure") {
        actionButton("Add New SDK").click()
        popupClick("Kotlin SDK")
        button("OK").click()
      }
    }
  }

  override fun isIdeFrameRun() = false
}