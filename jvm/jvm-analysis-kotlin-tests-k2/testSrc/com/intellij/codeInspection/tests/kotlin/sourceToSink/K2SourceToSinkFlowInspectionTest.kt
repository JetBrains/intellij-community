package com.intellij.codeInspection.tests.kotlin.sourceToSink

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2SourceToSinkFlowInspectionTest : KotlinSourceToSinkFlowInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}