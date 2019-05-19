// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectWizard.kotlin.installKotlinPlugin

import com.intellij.ide.projectWizard.kotlin.model.KotlinGuiTestCase
import com.intellij.ide.projectWizard.kotlin.model.KOTLIN_PLUGIN_NAME
import com.intellij.ide.projectWizard.kotlin.model.KotlinTestProperties
import com.intellij.testGuiFramework.util.scenarios.*
import com.intellij.testGuiFramework.util.step
import org.junit.Ignore
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import org.junit.Test

class InstallPluginGuiTest : KotlinGuiTestCase() {
  @Test
  fun installKotlinPlugin() {
    step("install Kotlin plugin, version '${KotlinTestProperties.kotlin_plugin_version_main}'") {
      //    TODO: uncomment when IDEA-198938, IDEA-198785 fixing
      //    if (pluginsDialogScenarios.isPluginRequiredVersionInstalled(
      //        KOTLIN_PLUGIN_NAME, KotlinTestProperties.kotlin_plugin_version_full
      //      ).not()) {
      pluginsDialogScenarios.actionAndRestart {
        pluginsDialogScenarios.installPluginFromDisk(KotlinTestProperties.kotlin_plugin_install_path)
      }
      //      assertTrue(
      //        actual = pluginsDialogScenarios.isPluginRequiredVersionInstalled(
      //          KOTLIN_PLUGIN_NAME, KotlinTestProperties.kotlin_plugin_version_full),
      //        message = "Kotlin plugin `${KotlinTestProperties.kotlin_plugin_version_full}` is not installed")
      //    }
    }
  }

  @Test
  @Ignore
  fun uninstallKotlinPlugin() {
    step("uninstall Kotlin plugin") {
      pluginsDialogScenarios.actionAndRestart {
        pluginsDialogScenarios.uninstallPlugin(KOTLIN_PLUGIN_NAME)
      }
      assertFalse(
        actual = pluginsDialogScenarios
          .isPluginRequiredVersionInstalled(KOTLIN_PLUGIN_NAME, KotlinTestProperties.kotlin_plugin_version_full),
        message = "Kotlin plugin `${KotlinTestProperties.kotlin_plugin_version_full}` is not uninstalled")
    }
  }

  override fun isIdeFrameRun(): Boolean = false
}