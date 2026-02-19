package com.intellij.customization.console

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K2LogFinderHyperlinkTest : KotlinLogFinderHyperlinkTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K2
}