// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigImportHelper
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.components.stateStore
import com.intellij.testFramework.ApplicationRule
import com.intellij.testFramework.rules.InMemoryFsRule
import com.intellij.util.io.createDirectories
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
  fun `find recent config directory on macOS`() {
    doTest(true)
  }

  @Test
  fun `find recent config directory`() {
    doTest(false)
  }

  private fun doTest(isMacOs: Boolean) {
    val fs = fsRule.fs
    writeStorageFile("2020.1", 100, isMacOs)
    writeStorageFile("2021.1", 200, isMacOs)
    writeStorageFile("2022.1", 300, isMacOs)

    val newConfigPath = fs.getPath("/data/${constructConfigPath("2022.3", isMacOs)}")
    // create new config dir to test that it will be not suggested too (as on start of new version config dir can be created)
    newConfigPath.createDirectories()

    assertThat(ConfigImportHelper.findRecentConfigDirectory(newConfigPath, isMacOs).joinToString("\n")).isEqualTo("""
        /data/${constructConfigPath("2022.1", isMacOs)}
        /data/${constructConfigPath("2021.1", isMacOs)}
        /data/${constructConfigPath("2020.1", isMacOs)}
      """.trimIndent())

    writeStorageFile("2021.1", 400, isMacOs)
    assertThat(ConfigImportHelper.findRecentConfigDirectory(newConfigPath, isMacOs).joinToString("\n")).isEqualTo("""
        /data/${constructConfigPath("2021.1", isMacOs)}
        /data/${constructConfigPath("2022.1", isMacOs)}
        /data/${constructConfigPath("2020.1", isMacOs)}
      """.trimIndent())
  }

  @Test
  fun `sort if no anchor files`() {
    val isMacOs = true
    fun writeStorageDir(version: String) {
      val dir = fsRule.fs.getPath("/data/" + (constructConfigPath(version, isMacOs)))
      dir.createDirectories()
    }

    val fs = fsRule.fs
    writeStorageDir("2022.1")
    writeStorageDir("2021.1")
    writeStorageDir("2020.1")

    val newConfigPath = fs.getPath("/data/${constructConfigPath("2022.3", isMacOs)}")
    // create new config dir to test that it will be not suggested too (as on start of new version config dir can be created)
    newConfigPath.createDirectories()

    assertThat(ConfigImportHelper.findRecentConfigDirectory(newConfigPath, isMacOs).joinToString("\n")).isEqualTo("""
        /data/${constructConfigPath("2022.1", isMacOs)}
        /data/${constructConfigPath("2021.1", isMacOs)}
        /data/${constructConfigPath("2020.1", isMacOs)}
      """.trimIndent())
  }

  private fun writeStorageFile(version: String, lastModified: Long, isMacOs: Boolean) {
    val dir = fsRule.fs.getPath("/data/" + (constructConfigPath(version, isMacOs)))
    val file = dir.resolve(PathManager.OPTIONS_DIRECTORY + '/' + StoragePathMacros.NOT_ROAMABLE_FILE)
    Files.setLastModifiedTime(file.write(version), FileTime.fromMillis(lastModified))
  }
}

private fun constructConfigPath(version: String, isMacOs: Boolean): String {
  return "${if (isMacOs) "" else "."}IntelliJIdea$version" + if (isMacOs) "" else "/${ConfigImportHelper.CONFIG}"
}

private fun description(block: () -> String) = object : Description() {
  override fun value() = block()
}