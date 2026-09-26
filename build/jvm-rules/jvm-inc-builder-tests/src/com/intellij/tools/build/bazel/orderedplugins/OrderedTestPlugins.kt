// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.orderedplugins

import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Test compiler plugins for checking `--x_compiler_plugin_order` handling: each registrar reports its
 * invocation to [PluginInvocationRecorder]. The registrars are standalone classes without a common base,
 * so that every on-the-fly plugin jar built by the test contains exactly one
 * [CompilerPluginRegistrar] implementation.
 */
@OptIn(ExperimentalCompilerApi::class)
class OrderedTestPluginFirst : CompilerPluginRegistrar() {
  override val pluginId: String = "tests.order.first"
  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    PluginInvocationRecorder.record(pluginId)
  }
}

@OptIn(ExperimentalCompilerApi::class)
class OrderedTestPluginSecond : CompilerPluginRegistrar() {
  override val pluginId: String = "tests.order.second"
  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    PluginInvocationRecorder.record(pluginId)
  }
}

@OptIn(ExperimentalCompilerApi::class)
class OrderedTestPluginThird : CompilerPluginRegistrar() {
  override val pluginId: String = "tests.order.third"
  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    PluginInvocationRecorder.record(pluginId)
  }
}
