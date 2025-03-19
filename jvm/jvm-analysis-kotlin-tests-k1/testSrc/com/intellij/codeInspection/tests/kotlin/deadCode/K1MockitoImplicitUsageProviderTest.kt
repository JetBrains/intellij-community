package com.intellij.codeInspection.tests.kotlin.deadCode

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1MockitoImplicitUsageProviderTest : KotlinMockitoImplicitUsageProviderTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K1
}