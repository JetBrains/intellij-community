package com.intellij.logging.highlighting

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1LoggingPlaceholderAnnotatorTest : KotlinLoggingPlaceholderAnnotatorTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K1
}