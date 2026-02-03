package com.intellij.customization.console

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1LogFinderHyperlinkTest : KotlinLogFinderHyperlinkTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K1
}