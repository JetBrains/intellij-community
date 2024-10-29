package com.intellij.codeInspection.tests.kotlin.test

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode
import org.junit.Ignore
import org.junit.runner.RunWith
import org.junit.runners.BlockJUnit4ClassRunner

@RunWith(BlockJUnit4ClassRunner::class)
@Ignore("IDEA-348567")
class K2AssertEqualsBetweenInconvertibleTypesInspectionTest : KotlinAssertEqualsBetweenInconvertibleTypesInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}