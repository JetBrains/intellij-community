package com.intellij.codeInspection.tests.kotlin.test

import com.intellij.idea.IgnoreJUnit3
import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.Ignore

@Ignore
@IgnoreJUnit3
class K2TestMethodWithoutAssertionInspectionTest : KotlinTestMethodWithoutAssertionInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}