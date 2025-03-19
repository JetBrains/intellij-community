package com.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

class K1PluginSanityTest : BasePlatformTestCase() {
  fun testK1PluginEnabled() {
    assertTrue(KotlinPluginModeProvider.isK1Mode())
  }

  fun testK2PluginDisabled() {
    assertFalse(KotlinPluginModeProvider.isK2Mode())
  }
}