package com.intellij.codeInspection.tests.kotlin.logging

import com.intellij.idea.IgnoreJUnit3
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.Ignore

@Ignore
@IgnoreJUnit3
class K2LoggingPlaceholderCountMatchesArgumentCountInspectionLog4J2Test : KotlinLoggingPlaceholderCountMatchesArgumentCountInspectionLog4J2Test() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}