// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package io.bazel.kotlin.plugin.jdeps

import org.jetbrains.kotlin.codegen.extensions.ClassFileFactoryFinalizerExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)
class JdepsGenComponentRegistrar : CompilerPluginRegistrar() {
  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val classUsageRecorder = ClassUsageRecorder()
    val genExtension = JdepsGenExtension(classUsageRecorder, configuration)
    FirExtensionRegistrarAdapter.registerExtension(JdepsFirExtensions(classUsageRecorder))
    ClassFileFactoryFinalizerExtension.registerExtension(genExtension)
  }
}