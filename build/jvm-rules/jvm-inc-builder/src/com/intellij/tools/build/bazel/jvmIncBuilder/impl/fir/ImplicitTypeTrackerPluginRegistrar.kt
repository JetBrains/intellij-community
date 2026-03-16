// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl.fir

import com.intellij.tools.build.bazel.jvmIncBuilder.impl.ImplicitTypeDependencyTracker
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration

/**
 * Compiler plugin registrar that registers the IR extension for tracking
 * inferred type dependencies on external definitions.
 */
@OptIn(ExperimentalCompilerApi::class)
class ImplicitTypeTrackerPluginRegistrar(
  private val tracker: ImplicitTypeDependencyTracker
) : CompilerPluginRegistrar() {

  override val pluginId: String
    get() = "com.intellij.tools.build.bazel.inferred-type-tracker"

  override val supportsK2: Boolean = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    IrGenerationExtension.registerExtension(ImplicitTypeIrExtension(tracker))
  }
}
