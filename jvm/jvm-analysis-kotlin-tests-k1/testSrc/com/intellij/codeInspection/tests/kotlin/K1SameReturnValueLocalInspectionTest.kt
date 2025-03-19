package com.intellij.codeInspection.tests.kotlin

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1SameReturnValueLocalInspectionTest : KotlinSameReturnValueLocalInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K1
}