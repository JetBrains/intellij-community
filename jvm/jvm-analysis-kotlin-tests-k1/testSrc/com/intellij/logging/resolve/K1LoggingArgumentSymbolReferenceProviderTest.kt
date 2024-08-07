package com.intellij.logging.resolve

import org.jetbrains.kotlin.idea.base.plugin.KotlinPluginMode

class K1LoggingArgumentSymbolReferenceProviderTest : KotlinLoggingArgumentSymbolReferenceProviderTest() {
  override val pluginMode: KotlinPluginMode get() = KotlinPluginMode.K1
}