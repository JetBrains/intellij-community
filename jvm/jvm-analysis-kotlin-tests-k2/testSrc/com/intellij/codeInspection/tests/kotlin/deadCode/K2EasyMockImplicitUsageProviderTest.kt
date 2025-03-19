package com.intellij.codeInspection.tests.kotlin.deadCode

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2EasyMockImplicitUsageProviderTest : KotlinEasyMockImplicitUsageProviderTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}