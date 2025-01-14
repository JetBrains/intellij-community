// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.cli.jvm.compiler

import com.intellij.openapi.Disposable
import org.jetbrains.kotlin.backend.jvm.jvmLoweringPhases
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.createPhaseConfig
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.modules.ModuleBuilder
import org.jetbrains.kotlin.cli.jvm.compiler.pipeline.compileModulesUsingFrontendIrAndLightTree
import org.jetbrains.kotlin.cli.jvm.compiler.pipeline.createProjectEnvironment
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.cli.jvm.configureAdvancedJvmOptions
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser.RegisteredPluginInfo
import org.jetbrains.kotlin.cli.plugins.processCompilerPluginOptions
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.PluginProcessingException
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.util.ServiceLoaderLite
import java.io.File
import java.net.URLClassLoader

private fun createCompilerConfigurationTemplate(): CompilerConfiguration {
  val config = CompilerConfiguration()
  config.put(CLIConfigurationKeys.FLEXIBLE_PHASE_CONFIG, createPhaseConfig(
    compoundPhase = jvmLoweringPhases,
    arguments = CommonCompilerArguments.DummyImpl(),
    messageCollector = MessageCollector.NONE,
  ))

  config.put(JVMConfigurationKeys.JDK_HOME, File(System.getProperty("java.home") ?: error("No java.home system property")))
  config.put(JVMConfigurationKeys.DISABLE_STANDARD_SCRIPT_DEFINITION, true)

  config.configureJdkClasspathRoots()

  config.messageCollector = MessageCollector.NONE
  config.configureAdvancedJvmOptions(K2JVMCompilerArguments())

  config.isReadOnly = true
  return config
}

internal val configTemplate = createCompilerConfigurationTemplate()

internal fun k2jvm(
  config: CompilerConfiguration,
  rootDisposable: Disposable,
  module: ModuleBuilder,
  messageCollector: MessageCollector,
): ExitCode {
  val moduleBuilders = listOf(module)

  val modularJdkRoot = module.modularJdkRoot
  if (modularJdkRoot != null) {
    // We use the SDK of the first module in the chunk, which is not always correct because some other module in the chunk
    // might depend on a different SDK
    config.put(JVMConfigurationKeys.JDK_HOME, File(modularJdkRoot))
  }

  require(config.getBoolean(CommonConfigurationKeys.USE_LIGHT_TREE))
  require(config.getBoolean(CommonConfigurationKeys.USE_FIR))
  if (messageCollector.hasErrors()) {
    return ExitCode.COMPILATION_ERROR
  }

  val projectEnvironment = createProjectEnvironment(
    configuration = config,
    parentDisposable = rootDisposable,
    configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES,
    messageCollector = messageCollector,
  )
  if (messageCollector.hasErrors()) {
    return ExitCode.COMPILATION_ERROR
  }

  if (!FirKotlinToJvmBytecodeCompiler.checkNotSupportedPlugins(config, messageCollector)) {
    return ExitCode.COMPILATION_ERROR
  }

  if (compileModulesUsingFrontendIrAndLightTree(
      projectEnvironment = projectEnvironment,
      compilerConfiguration = config,
      messageCollector = messageCollector,
      buildFile = null,
      chunk = moduleBuilders,
      targetDescription = module.getModuleName() + "-" + module.getModuleType(),
      checkSourceFiles = false,
      isPrintingVersion = false,
    )) {
    return ExitCode.OK
  }
  return ExitCode.COMPILATION_ERROR
}

@OptIn(ExperimentalCompilerApi::class)
internal fun loadPlugins(
  configuration: CompilerConfiguration,
  pluginConfigurations: List<CompilerPluginDescriptor>
) {
  for (pluginInfo in loadRegisteredPluginsInfo(pluginConfigurations)) {
    pluginInfo.compilerPluginRegistrar?.let { configuration.add(CompilerPluginRegistrar.COMPILER_PLUGIN_REGISTRARS, it) }
    if (pluginInfo.pluginOptions.isEmpty()) {
      continue
    }

    val commandLineProcessor = pluginInfo.commandLineProcessor!!
    processCompilerPluginOptions(processor = commandLineProcessor, pluginOptions = pluginInfo.pluginOptions, configuration = configuration)
  }
}

@OptIn(ExperimentalCompilerApi::class)
@Suppress("DEPRECATION")
private fun loadRegisteredPluginsInfo(pluginConfigurations: List<CompilerPluginDescriptor>): Sequence<RegisteredPluginInfo> {
  return pluginConfigurations.asSequence().map { pluginConfiguration ->
    pluginConfiguration.info?.let {
      return@map it
    }

    val files = pluginConfiguration.classpath.map { File(it) }
    val classLoader = URLClassLoader(
      files.map { it.toURI().toURL() }.toTypedArray(),
      CompilerConfiguration::class.java.classLoader
    )
    val compilerPluginRegistrars = ServiceLoaderLite.loadImplementations(CompilerPluginRegistrar::class.java, files, classLoader)
    fun multiplePluginsErrorMessage(pluginObjects: List<Any>): String {
      return buildString {
        append("Multiple plugins found in given classpath: ")
        appendLine(pluginObjects.mapNotNull { it::class.qualifiedName }.joinToString(", "))
        append("  Plugin configuration is: $pluginConfiguration")
      }
    }

    when (compilerPluginRegistrars.size) {
      0 -> throw PluginProcessingException("No plugins found in given classpath: $pluginConfiguration")
      1 -> {}
      else -> throw PluginProcessingException(multiplePluginsErrorMessage(compilerPluginRegistrars))
    }

    val commandLineProcessor = ServiceLoaderLite.loadImplementations(CommandLineProcessor::class.java, files, classLoader)
    if (commandLineProcessor.size > 1) {
      throw PluginProcessingException(multiplePluginsErrorMessage(commandLineProcessor))
    }

    RegisteredPluginInfo(
      componentRegistrar = null,
      compilerPluginRegistrar = compilerPluginRegistrars.firstOrNull(),
      commandLineProcessor = commandLineProcessor.firstOrNull(),
      pluginOptions = pluginConfiguration.options,
    )
  }
}