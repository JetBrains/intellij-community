package com.intellij.codeInspection.tests.kotlin.logging

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1LoggingGuardedByConditionInspectionTest : KotlinLoggingGuardedByConditionInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K1
}