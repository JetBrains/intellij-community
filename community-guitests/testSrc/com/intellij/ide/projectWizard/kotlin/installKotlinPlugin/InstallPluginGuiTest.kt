// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.installKotlinPlugin

import com.intellij.ide.projectWizard.kotlin.model.KotlinGuiTestCase
import com.intellij.ide.projectWizard.kotlin.model.KOTLIN_PLUGIN_NAME
import com.intellij.ide.projectWizard.kotlin.model.KOTLIN_PLUGIN_INSTALL_PATH
import com.intellij.ide.projectWizard.kotlin.model.KOTLIN_PLUGIN_VERSION
import com.intellij.testGuiFramework.util.scenarios.*
import org.junit.Ignore
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.junit.Test

class InstallPluginGuiTest : KotlinGuiTestCase() {
  @Test
  fun installKotlinPlugin() {
    pluginsDialogScenarios.actionAndRestart {
      pluginsDialogScenarios.installPluginFromDisk(KOTLIN_PLUGIN_INSTALL_PATH)
    }
    assertTrue(
      actual = pluginsDialogScenarios
        .isPluginRequiredVersionInstalled(KOTLIN_PLUGIN_NAME, KOTLIN_PLUGIN_VERSION),
      message = "Kotlin plugin `$KOTLIN_PLUGIN_VERSION` is not installed")
  }

  @Test
  @Ignore
  fun uninstallKotlinPlugin() {
    pluginsDialogScenarios.actionAndRestart {
      pluginsDialogScenarios.uninstallPlugin(KOTLIN_PLUGIN_NAME)
    }
    assertFalse(
      actual = pluginsDialogScenarios
        .isPluginRequiredVersionInstalled(KOTLIN_PLUGIN_NAME, KOTLIN_PLUGIN_VERSION),
      message = "Kotlin plugin `$KOTLIN_PLUGIN_VERSION` is not uninstalled")
  }

  override fun isIdeFrameRun(): Boolean = false
}