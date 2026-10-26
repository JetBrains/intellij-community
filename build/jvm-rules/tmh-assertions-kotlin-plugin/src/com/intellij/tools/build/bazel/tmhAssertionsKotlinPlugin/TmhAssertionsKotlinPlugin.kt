// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.tmhAssertionsKotlinPlugin

import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.jvm.extensions.ClassGeneratorExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * A kotlinc plugin that adds the threading model assertions to the Kotlin classes of the running compile. A method with
 * a threading annotation gets the call of the matching soft assertion of `ThreadingAssertions` before its first
 * instruction, as the jvm-inc-builder worker adds it to the Java output. The plugin changes no other method and no other
 * class, so a class without an annotated method keeps the bytes of a compile without the plugin.
 *
 * The compiler finds the plugin with the service loader on the plugin classpath. [TmhAssertionsCommandLineProcessor]
 * declares the options. The plugin registers its extensions only when the option `enabled` is true.
 * [ThreadingAssertionsDeclaration] resolves the assertion class before the code generation, and
 * [TmhAssertionsClassGeneratorExtension] adds the calls during it.
 */
@OptIn(ExperimentalCompilerApi::class)
class TmhAssertionsKotlinPlugin : CompilerPluginRegistrar() {
  override val pluginId: String
    get() = PLUGIN_ID

  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    if (!configuration.getBoolean(TmhAssertionsCommandLineProcessor.ENABLED_KEY)) {
      return
    }
    val threadingAssertions = ThreadingAssertionsDeclaration()
    IrGenerationExtension.registerExtension(threadingAssertions)
    ClassGeneratorExtension.registerExtension(
      TmhAssertionsClassGeneratorExtension(
        threadingAssertions,
        generateLineNumbers = configuration.getBoolean(TmhAssertionsCommandLineProcessor.GENERATE_LINE_NUMBERS_KEY),
      )
    )
  }

  companion object {
    const val PLUGIN_ID: String = "com.intellij.tools.build.bazel.tmh-assertions"
  }
}
