package com.intellij.codeInspection.tests.kotlin.test

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2TestCaseWithoutTestsInspectionTest : KotlinTestCaseWithoutTestsInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}