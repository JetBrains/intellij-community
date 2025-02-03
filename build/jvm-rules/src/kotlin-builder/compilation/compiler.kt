// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.cli.jvm.compiler

import org.jetbrains.kotlin.backend.common.phaser.then
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.createPhaseConfig
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler.K2JVMCompilerPerformanceManager
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.cli.jvm.configureAdvancedJvmOptions
import org.jetbrains.kotlin.cli.jvm.plugins.PluginCliParser.RegisteredPluginInfo
import org.jetbrains.kotlin.cli.pipeline.AbstractCliPipeline
import org.jetbrains.kotlin.cli.pipeline.AbstractConfigurationPhase
import org.jetbrains.kotlin.cli.pipeline.ArgumentsPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.CheckCompilationErrors
import org.jetbrains.kotlin.cli.pipeline.ConfigurationPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.PipelineContext
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmBackendPipelinePhase
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmBinaryPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmFir2IrPipelinePhase
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmFrontendPipelinePhase
import org.jetbrains.kotlin.cli.plugins.processCompilerPluginOptions
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.PluginProcessingException
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.config.phaseConfig
import org.jetbrains.kotlin.config.phaser.CompilerPhase
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.util.ServiceLoaderLite
import java.io.File
import java.net.URLClassLoader

private fun createCompilerConfigurationTemplate(): CompilerConfiguration {
  val config = CompilerConfiguration()
  config.phaseConfig = createPhaseConfig(arguments = CommonCompilerArguments.DummyImpl())

  config.put(JVMConfigurationKeys.JDK_HOME, File(System.getProperty("java.home") ?: error("No java.home system property")))
  config.put(JVMConfigurationKeys.DISABLE_STANDARD_SCRIPT_DEFINITION, true)

  config.configureJdkClasspathRoots()

  config.messageCollector = MessageCollector.NONE
  config.configureAdvancedJvmOptions(K2JVMCompilerArguments())

  config.isReadOnly = true
  return config
}

internal val configTemplate = createCompilerConfigurationTemplate()

internal class BazelJvmCliPipeline(
  private val configPhase: BazelJvmConfigurationPipelinePhase,
) : AbstractCliPipeline<K2JVMCompilerArguments>() {
  override val defaultPerformanceManager: K2JVMCompilerPerformanceManager = K2JVMCompilerPerformanceManager()

  override fun createCompoundPhase(arguments: K2JVMCompilerArguments): CompilerPhase<PipelineContext, ArgumentsPipelineArtifact<K2JVMCompilerArguments>, *> {
    return createRegularPipeline()
  }

  private fun createRegularPipeline(): CompilerPhase<PipelineContext, ArgumentsPipelineArtifact<K2JVMCompilerArguments>, JvmBinaryPipelineArtifact> {
    // instead of JvmConfigurationPipelinePhase, we use our own BazelJvmConfigurationPipelinePhase
    return configPhase then
      JvmFrontendPipelinePhase then
      JvmFir2IrPipelinePhase then
      JvmBackendPipelinePhase
  }
}

internal class BazelJvmConfigurationPipelinePhase(
  private val config: CompilerConfiguration,
) : AbstractConfigurationPhase<K2JVMCompilerArguments>(
  name = "JvmConfigurationPipelinePhase",
  postActions = setOf(CheckCompilationErrors.CheckMessageCollector),
  configurationUpdaters = emptyList()
) {
  override fun executePhase(input: ArgumentsPipelineArtifact<K2JVMCompilerArguments>): ConfigurationPipelineArtifact? {
    return ConfigurationPipelineArtifact(config, input.diagnosticCollector, input.rootDisposable)
  }

  override fun createMetadataVersion(versionArray: IntArray): BinaryVersion = MetadataVersion(*versionArray)
}

//internal fun k2jvm(
//  config: CompilerConfiguration,
//  rootDisposable: Disposable,
//  module: ModuleBuilder,
//  messageCollector: MessageCollector,
//): ExitCode {
//  val moduleBuilders = listOf(module)
//
//  val modularJdkRoot = module.modularJdkRoot
//  if (modularJdkRoot != null) {
//    // We use the SDK of the first module in the chunk, which is not always correct because some other module in the chunk
//    // might depend on a different SDK
//    config.put(JVMConfigurationKeys.JDK_HOME, File(modularJdkRoot))
//  }
//
//  require(config.getBoolean(CommonConfigurationKeys.USE_LIGHT_TREE))
//  require(config.getBoolean(CommonConfigurationKeys.USE_FIR))
//  if (messageCollector.hasErrors()) {
//    return ExitCode.COMPILATION_ERROR
//  }
//
//  val projectEnvironment = createCoreEnvironment(
//    configuration = config,
//    rootDisposable = rootDisposable,
//    configFiles = EnvironmentConfigFiles.JVM_CONFIG_FILES,
//    messageCollector = messageCollector,
//  )
//  if (messageCollector.hasErrors()) {
//    return ExitCode.COMPILATION_ERROR
//  }
//
//  if (compileModulesUsingFrontendIrAndLightTree(
//      projectEnvironment = projectEnvironment,
//      compilerConfiguration = config,
//      messageCollector = messageCollector,
//      buildFile = null,
//      chunk = moduleBuilders,
//      targetDescription = module.getModuleName() + "-" + module.getModuleType(),
//      checkSourceFiles = false,
//      isPrintingVersion = false,
//    )) {
//    return ExitCode.OK
//  }
//  return ExitCode.COMPILATION_ERROR
//}

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