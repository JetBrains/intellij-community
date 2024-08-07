package com.intellij.codeInspection.tests.kotlin.logging

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2LoggingPlaceholderCountMatchesArgumentCountInspectionLog4J2Test : KotlinLoggingPlaceholderCountMatchesArgumentCountInspectionLog4J2Test() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}