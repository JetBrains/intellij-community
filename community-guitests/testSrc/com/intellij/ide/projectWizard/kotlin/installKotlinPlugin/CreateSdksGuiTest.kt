// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.installKotlinPlugin

import com.intellij.ide.projectWizard.kotlin.model.KotlinGuiTestCase
import com.intellij.testGuiFramework.framework.Timeouts
import com.intellij.testGuiFramework.impl.*
import com.intellij.testGuiFramework.util.*
import org.fest.swing.exception.ComponentLookupException
import org.junit.Test

class CreateSdksGuiTest : KotlinGuiTestCase() {
  private val dialogName = "Project Structure for New Projects"

  @Test
  fun createKotlinSdk(){
    step("create a Kotlin SDK") {
      welcomeFrame {
        actionLink("Configure").click()
        // starting from 191
        popupMenu("Structure for New Projects").clickSearchedItem()
          dialog(dialogName) {
            jList("SDKs").clickItem("SDKs")
            val kotlinSdk = "Kotlin SDK"
            try {
              jTree(kotlinSdk, timeout = Timeouts.noTimeout)
              logInfo("'$kotlinSdk' exists")
            }
            catch (e: ComponentLookupException) {
              step("create '$kotlinSdk' as it's absent") {
                actionButton("Add New SDK").click()
                popupMenu(kotlinSdk).clickSearchedItem()
                step("check whether '$kotlinSdk' created") {
                  jTree(kotlinSdk, timeout = Timeouts.seconds05)
                }
              }
            }
            finally {
              step("close `$dialogName` dialog with OK") {
                button("OK").clickWhenEnabled(Timeouts.seconds05)
              }
            }
          }
      }
    }
  }

  override fun isIdeFrameRun() = false
}