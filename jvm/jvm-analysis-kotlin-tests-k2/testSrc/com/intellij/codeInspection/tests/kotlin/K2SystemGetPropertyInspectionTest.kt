package com.intellij.codeInspection.tests.kotlin

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2SystemGetPropertyInspectionTest : KotlinSystemGetPropertyInspectionTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}