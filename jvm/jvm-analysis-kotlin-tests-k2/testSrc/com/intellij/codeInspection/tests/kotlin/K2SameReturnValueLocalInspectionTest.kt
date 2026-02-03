package com.intellij.codeInspection.tests.kotlin

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2SameReturnValueLocalInspectionTest : KotlinSameReturnValueLocalInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}