package com.intellij.codeInspection.tests.kotlin.test

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1TestFailedLineInspectionTest : KotlinTestFailedLineInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K1
}