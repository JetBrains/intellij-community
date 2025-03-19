package com.intellij.codeInspection.tests.kotlin.sourceToSink

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1MarkAsSafeFixSourceToSinkFlowInspectionTest : KotlinMarkAsSafeFixSourceToSinkFlowInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K1
}