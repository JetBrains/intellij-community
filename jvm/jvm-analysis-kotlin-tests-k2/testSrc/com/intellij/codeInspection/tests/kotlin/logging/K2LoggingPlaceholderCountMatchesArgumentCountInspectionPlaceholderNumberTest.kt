package com.intellij.codeInspection.tests.kotlin.logging

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2LoggingPlaceholderCountMatchesArgumentCountInspectionPlaceholderNumberTest : KotlinLoggingPlaceholderCountMatchesArgumentCountInspectionPlaceholderNumberTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}