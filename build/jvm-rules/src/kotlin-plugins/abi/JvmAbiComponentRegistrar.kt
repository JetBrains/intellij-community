// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.jvm.abi

import org.jetbrains.kotlin.backend.jvm.extensions.ClassGeneratorExtension
import org.jetbrains.kotlin.codegen.extensions.ClassFileFactoryFinalizerExtension
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import java.io.File

@OptIn(ExperimentalCompilerApi::class)
class JvmAbiComponentRegistrar : CompilerPluginRegistrar() {
    override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
        val outputPath = configuration.getNotNull(JvmAbiConfigurationKeys.OUTPUT_PATH)
        configuration.put(JVMConfigurationKeys.RETAIN_OUTPUT_IN_MEMORY, true)
        val removeDataClassCopy = configuration.getBoolean(JvmAbiConfigurationKeys.REMOVE_DATA_CLASS_COPY_IF_CONSTRUCTOR_IS_PRIVATE)
        val builderExtension = JvmAbiClassBuilderInterceptor(
          removeDataClassCopyIfConstructorIsPrivate = removeDataClassCopy,
          removePrivateClasses = configuration.getBoolean(JvmAbiConfigurationKeys.REMOVE_PRIVATE_CLASSES),
          treatInternalAsPrivate = configuration.getBoolean(JvmAbiConfigurationKeys.TREAT_INTERNAL_AS_PRIVATE),
        )
        val outputExtension = JvmAbiOutputExtension(
          outputPath = File(outputPath),
          targetLabel = configuration.getNotNull(JvmAbiConfigurationKeys.TARGET_LABEL),
          abiClassInfoBuilder = builderExtension::buildAbiClassInfoAndReleaseResources,
          removeDebugInfo = configuration.getBoolean(JvmAbiConfigurationKeys.REMOVE_DEBUG_INFO),
          removeDataClassCopyIfConstructorIsPrivate = removeDataClassCopy,
          preserveDeclarationOrder = configuration.getBoolean(JvmAbiConfigurationKeys.PRESERVE_DECLARATION_ORDER),
          treatInternalAsPrivate = configuration.getBoolean(JvmAbiConfigurationKeys.TREAT_INTERNAL_AS_PRIVATE),
        )

        ClassGeneratorExtension.registerExtension(builderExtension)
        ClassFileFactoryFinalizerExtension.registerExtension(outputExtension)
    }

    override val supportsK2: Boolean
        get() = true
}
