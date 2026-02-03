package com.intellij

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginModeProvider

class K2PluginSanityTest : BasePlatformTestCase() {
  fun testK2PluginEnabled() {
    assertTrue(KotlinPluginModeProvider.isK2Mode())
  }

  fun testK1PluginDisabled() {
    assertFalse(KotlinPluginModeProvider.isK1Mode())
  }
}