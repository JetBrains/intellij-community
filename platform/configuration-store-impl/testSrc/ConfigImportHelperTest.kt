// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.stateStore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.fixtures.BareTestFixtureTestCase
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.description.Description
import org.junit.ClassRule
import org.junit.Test
import java.io.File

class ConfigImportHelperTest : BareTestFixtureTestCase() {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @Test
  fun configDirectoryIsValidForImport() {
    PropertiesComponent.getInstance().setValue("property.ConfigImportHelperTest", true)
    try {
      useAppConfigDir {
        runBlocking { ApplicationManager.getApplication().stateStore.save(true) }

        val config = File(PathManager.getConfigPath())
        assertThat(ConfigImportHelper.isConfigDirectory(config))
          .`as`(description {
            "${config} exists=${config.exists()} options=${File(config, "options").list().asList()}"
          })
          .isTrue()
      }
    }
    finally {
      PropertiesComponent.getInstance().unsetValue("property.ConfigImportHelperTest")
    }
  }
}

private fun description(block: () -> String) = object : Description() {
  override fun value() = block()
}