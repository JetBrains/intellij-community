package com.intellij.codeInspection.tests.kotlin.performance

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1UrlHashCodeInspectionTest : KotlinUrlHashCodeInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K1
}