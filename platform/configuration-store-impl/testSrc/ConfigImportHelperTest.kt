// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.stateStore
import com.intellij.openapi.util.SystemInfo
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.directoryStreamIfExists
import com.intellij.util.io.exists
import com.intellij.util.io.write
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.description.Description
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.attribute.FileTime

class ConfigImportHelperTest {
  companion object {
    @JvmField
    @ClassRule
    val appRule = ApplicationRule()
  }

  @JvmField
  @Rule
  val fsRule = InMemoryFsRule()

  @Test
  fun configDirectoryIsValidForImport() {
    PropertiesComponent.getInstance().setValue("property.ConfigImportHelperTest", true)
    try {
      useAppConfigDir {
        runBlocking { ApplicationManager.getApplication().stateStore.save(forceSavingAllSettings = true) }

        val config = Paths.get(PathManager.getConfigPath())
        assertThat(ConfigImportHelper.isConfigDirectory(config))
          .`as`(description {
            "${config} exists=${config.exists()} options=${config.resolve("options").directoryStreamIfExists { it.toList() }}"
          })
          .isTrue()
      }
    }
    finally {
      PropertiesComponent.getInstance().unsetValue("property.ConfigImportHelperTest")
    }
  }

  @Test
  fun `find recent config directory`() {
    val fs = fsRule.fs
    writeStorageFile("2020.1", 100)
    writeStorageFile("2021.1", 200)
    writeStorageFile("2022.1", 300)
    val newConfigPath = fs.getPath("/data/${constructConfigPath("2022.1")}")
    assertThat(ConfigImportHelper.findRecentConfigDirectory(newConfigPath).joinToString("\n")).isEqualTo("""
      /data/IntelliJIdea2022.1
      /data/IntelliJIdea2021.1
      /data/IntelliJIdea2020.1
    """.trimIndent())

    writeStorageFile("2021.1", 400)
    assertThat(ConfigImportHelper.findRecentConfigDirectory(newConfigPath).joinToString("\n")).isEqualTo("""
      /data/IntelliJIdea2021.1
      /data/IntelliJIdea2022.1
      /data/IntelliJIdea2020.1
    """.trimIndent())
  }

  private fun writeStorageFile(version: String, lastModified: Long) {
    val path = fsRule.fs.getPath("/data/" + (constructConfigPath(version)),
                                 PathManager.OPTIONS_DIRECTORY + '/' + StoragePathMacros.NOT_ROAMABLE_FILE)
    Files.setLastModifiedTime(path.write(version), FileTime.fromMillis(lastModified))
  }

}

private fun constructConfigPath(version: String): String {
  return "IntelliJIdea$version" + if (SystemInfo.isMac) "" else "/${ConfigImportHelper.CONFIG}"
}

private fun description(block: () -> String) = object : Description() {
  override fun value() = block()
}