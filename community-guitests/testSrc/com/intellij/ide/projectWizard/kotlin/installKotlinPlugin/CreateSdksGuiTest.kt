// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.installKotlinPlugin

import com.intellij.ide.projectWizard.kotlin.model.KotlinGuiTestCase
import com.intellij.ide.projectWizard.kotlin.model.KotlinTestProperties
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.logInfo
import com.intellij.testGuiFramework.util.logTestStep
import com.intellij.testGuiFramework.util.logUIStep
import org.fest.swing.exception.ComponentLookupException
import org.junit.Test

class CreateSdksGuiTest : KotlinGuiTestCase() {
  val dialogName = "Project Structure for New Projects"
  @Test
  fun createJdk(){
    logTestStep("Create a JDK on the path `${KotlinTestProperties.jdk_path}`")
    welcomeFrame {
      actionLink("Configure").click()
      popupClick("Project Defaults")
      popupClick("Project Structure")
      logUIStep("Open `$dialogName` dialog")
      dialog(dialogName) {
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
      logUIStep("Open `$dialogName` dialog")
      dialog(dialogName) {
        jList("SDKs").clickItem("SDKs")
        val kotlinSdk = "Kotlin SDK"
        try{
          jTree(kotlinSdk, timeout = 1L)
          logInfo("$kotlinSdk exists")
        }
        catch (e: ComponentLookupException){
          logUIStep("Going to create $kotlinSdk")
          actionButton("Add New SDK").click()
          popupClick(kotlinSdk)
          logUIStep("Going to check whether $kotlinSdk created")
          jTree(kotlinSdk, timeout = 1L)
        }
        finally {
          logUIStep("Close `$dialogName` dialog with OK")
          button("OK").click()
        }
      }
    }
  }

  override fun isIdeFrameRun() = false
}