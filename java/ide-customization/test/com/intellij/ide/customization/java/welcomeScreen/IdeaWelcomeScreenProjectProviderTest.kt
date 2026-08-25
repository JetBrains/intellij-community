// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.customization.java.welcomeScreen

import com.intellij.openapi.options.advanced.AdvancedSettings
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.SystemProperty
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

@TestApplication
@SystemProperty(propertyKey = "idea.force.disable.non.modal.welcome.screen", propertyValue = "false")
internal class IdeaWelcomeScreenProjectProviderTest {
  private val nonModalWelcomeScreenSettingId = "welcome.screen.non.modal.enabled"
  private val provider = IdeaWelcomeScreenProjectProvider()

  @Test
  @RegistryKey(key = "idea.welcome.screen.non.modal.enabled", value = "true")
  fun `regular file is claimed when both settings are enabled`(@TempDir tempDir: Path) = withAdvancedSetting(true) {
    val file = Files.writeString(tempDir.resolve("standalone.txt"), "text")

    assertTrue(provider.canOpenFilesFromSystemFileManager(file))
  }

  @Test
  @RegistryKey(key = "idea.welcome.screen.non.modal.enabled", value = "false")
  fun `regular file is not claimed when registry flag is disabled`(@TempDir tempDir: Path) = withAdvancedSetting(true) {
    val file = Files.writeString(tempDir.resolve("standalone.txt"), "text")

    assertFalse(provider.canOpenFilesFromSystemFileManager(file))
  }

  @Test
  @RegistryKey(key = "idea.welcome.screen.non.modal.enabled", value = "true")
  fun `regular file is not claimed when advanced setting is disabled`(@TempDir tempDir: Path) = withAdvancedSetting(false) {
    val file = Files.writeString(tempDir.resolve("standalone.txt"), "text")

    assertFalse(provider.canOpenFilesFromSystemFileManager(file))
  }

  @Test
  @RegistryKey(key = "idea.welcome.screen.non.modal.enabled", value = "true")
  fun `directories and missing paths are not claimed`(@TempDir tempDir: Path) = withAdvancedSetting(true) {
    assertFalse(provider.canOpenFilesFromSystemFileManager(tempDir))
    assertFalse(provider.canOpenFilesFromSystemFileManager(tempDir.resolve("missing.txt")))
  }

  private fun withAdvancedSetting(value: Boolean, action: () -> Unit) {
    val previousValue = AdvancedSettings.getBoolean(nonModalWelcomeScreenSettingId)
    try {
      AdvancedSettings.setBoolean(nonModalWelcomeScreenSettingId, value)
      action()
    }
    finally {
      AdvancedSettings.setBoolean(nonModalWelcomeScreenSettingId, previousValue)
    }
  }
}
