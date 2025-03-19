package com.intellij.codeInspection.tests.kotlin.performance

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2UrlHashCodeInspectionTest : KotlinUrlHashCodeInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}