// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "INVISIBLE_REFERENCE", "INVISIBLE_MEMBER", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet")

// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import com.intellij.psi.PsiJavaModule.MODULE_INFO_FILE
import org.jetbrains.bazel.jvm.util.ArgMap
import org.jetbrains.kotlin.backend.common.output.OutputFile
import org.jetbrains.kotlin.backend.common.output.OutputFileCollection
import org.jetbrains.kotlin.backend.common.phaser.then
import org.jetbrains.kotlin.backend.jvm.JvmIrCodegenFactory
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.CommonCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.config.addKotlinSourceRoot
import org.jetbrains.kotlin.cli.common.createPhaseConfig
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.moduleChunk
import org.jetbrains.kotlin.cli.common.modules.ModuleBuilder
import org.jetbrains.kotlin.cli.common.modules.ModuleChunk
import org.jetbrains.kotlin.cli.common.setupCommonArguments
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler.K2JVMCompilerPerformanceManager
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler.toBackendInput
import org.jetbrains.kotlin.cli.jvm.config.JvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.JvmModulePathRoot
import org.jetbrains.kotlin.cli.jvm.config.addJavaSourceRoot
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.cli.jvm.configureAdvancedJvmOptions
import org.jetbrains.kotlin.cli.jvm.setupJvmSpecificArguments
import org.jetbrains.kotlin.cli.pipeline.AbstractCliPipeline
import org.jetbrains.kotlin.cli.pipeline.AbstractConfigurationPhase
import org.jetbrains.kotlin.cli.pipeline.ArgumentsPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.CheckCompilationErrors
import org.jetbrains.kotlin.cli.pipeline.ConfigurationPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.PerformanceNotifications
import org.jetbrains.kotlin.cli.pipeline.PipelineContext
import org.jetbrains.kotlin.cli.pipeline.PipelinePhase
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmBinaryPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmFir2IrPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmFir2IrPipelinePhase
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmFrontendPipelinePhase
import org.jetbrains.kotlin.cli.plugins.processCompilerPluginOptions
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.messageCollector
import org.jetbrains.kotlin.config.moduleName
import org.jetbrains.kotlin.config.modules
import org.jetbrains.kotlin.config.phaseConfig
import org.jetbrains.kotlin.config.phaser.CompilerPhase
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmBackendClassResolver
import org.jetbrains.kotlin.fir.backend.jvm.FirJvmBackendExtension
import org.jetbrains.kotlin.fir.backend.utils.extractFirDeclarations
import org.jetbrains.kotlin.metadata.deserialization.BinaryVersion
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.modules.JavaRootPath
import java.io.File
import java.nio.file.Path

private val configTemplate = createCompilerConfigurationTemplate()

// configureModule must be also called after
@OptIn(ExperimentalCompilerApi::class)
fun prepareCompilerConfiguration(
  args: ArgMap<JvmBuilderFlags>,
  kotlinArgs: K2JVMCompilerArguments,
  baseDir: Path,
  abiOutputConsumer: (List<OutputFile>) -> Unit,
): CompilerConfiguration {
  val config = configTemplate.copy()
  config.put(JVMConfigurationKeys.RETAIN_OUTPUT_IN_MEMORY, true)
  configurePlugins(
    args = args,
    workingDir = baseDir,
    targetLabel = args.mandatorySingle(JvmBuilderFlags.TARGET_LABEL),
    abiOutputConsumer = abiOutputConsumer,
  ) {
    config.add(CompilerPluginRegistrar.COMPILER_PLUGIN_REGISTRARS, it.compilerPluginRegistrar!!)
    if (!it.pluginOptions.isEmpty()) {
      processCompilerPluginOptions(processor = it.commandLineProcessor!!, pluginOptions = it.pluginOptions, configuration = config)
    }
  }

  config.setupCommonArguments(kotlinArgs) { MetadataVersion(*it) }
  config.setupJvmSpecificArguments(kotlinArgs)
  if (args.boolFlag(JvmBuilderFlags.ALLOW_KOTLIN_PACKAGE)) {
    config.put(CLIConfigurationKeys.ALLOW_KOTLIN_PACKAGE, true)
  }
  return config
}

fun createJvmPipeline(
  config: CompilerConfiguration,
  checkCancelled: () -> Unit,
  consumer: (OutputFileCollection) -> Unit,
): AbstractCliPipeline<K2JVMCompilerArguments> {
  return BazelJvmCliPipeline(BazelJvmConfigurationPipelinePhase(config), checkCancelled, consumer)
}

fun configureModule(
  moduleName: String,
  config: CompilerConfiguration,
  outFileOrDirPath: String,
  args: ArgMap<JvmBuilderFlags>,
  baseDir: Path,
  allSources: List<Path>,
  // if incremental compilation
  changedKotlinSources: Sequence<String>?,
  classPath: Array<Path>,
): ModuleBuilder {
  var isJava9Module = false
  config.moduleName = moduleName

  val module = ModuleBuilder(name = moduleName, outputDir = outFileOrDirPath, type = "java-production")

  args.optional(JvmBuilderFlags.FRIENDS)?.let { value ->
    for (path in value) {
      module.addFriendDir(baseDir.resolve(path).normalize().toString())
    }
  }

  val moduleInfoNameSuffix = File.separatorChar + MODULE_INFO_FILE
  for (source in allSources) {
    val path = source.toString()
    if (path.endsWith(".java")) {
      module.addJavaSourceRoot(JavaRootPath(path, null))
      config.addJavaSourceRoot(source.toFile(), null)
      if (!isJava9Module) {
        isJava9Module = path.endsWith(moduleInfoNameSuffix)
      }
    }
    else if (changedKotlinSources == null) {
      module.addSourceFiles(path)
      config.addKotlinSourceRoot(path = path, isCommon = false, hmppModuleName = null)
    }
  }

  if (changedKotlinSources != null) {
    for (path in changedKotlinSources) {
      require(!path.endsWith(".java"))
      module.addSourceFiles(path)
      config.addKotlinSourceRoot(path = path, isCommon = false, hmppModuleName = null)
    }
  }

  for (path in classPath) {
    module.addClasspathEntry(path.toString())
  }

  for (file in classPath) {
    val ioFile = file.toFile()
    if (isJava9Module) {
      config.add(CLIConfigurationKeys.CONTENT_ROOTS, JvmModulePathRoot(ioFile))
    }
    config.add(CLIConfigurationKeys.CONTENT_ROOTS, JvmClasspathRoot(ioFile))
  }

  val modules = listOf(module)
  config.modules = modules
  config.moduleChunk = ModuleChunk(modules)
  return module
}

@OptIn(ExperimentalCompilerApi::class)
fun getDebugInfoAboutPlugins(args: ArgMap<JvmBuilderFlags>, baseDir: Path, targetLabel: String): String {
  val sb = StringBuilder()
  configurePlugins(args = args, workingDir = baseDir, targetLabel = targetLabel, abiOutputConsumer = null) { info ->
    sb.append(info.compilerPluginRegistrar!!.toString())
    if (!info.pluginOptions.isEmpty()) {
      sb.append("(" + info.pluginOptions.joinToString(separator = ", ") + ")")
    }
    sb.append('\n')
  }
  return sb.toString()
}

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

private class BazelJvmCliPipeline(
  private val configPhase: BazelJvmConfigurationPipelinePhase,
  private val checkCancelled: () -> Unit,
  private val consumer: (OutputFileCollection) -> Unit,
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
      BazelJvmBackendPipelinePhase(consumer, checkCancelled)
  }
}

private class BazelJvmConfigurationPipelinePhase(
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

// https://youtrack.jetbrains.com/issue/KT-75033/split-JvmBackendPipelinePhase-to-be-able-to-provide-a-custom-implementation-of-writeOutputs
private class BazelJvmBackendPipelinePhase(
  private val consumer: (OutputFileCollection) -> Unit,
  private val checkCancelled: () -> Unit,
) : PipelinePhase<JvmFir2IrPipelineArtifact, JvmBinaryPipelineArtifact>(
  name = "JvmBackendPipelineStep",
  preActions = setOf(
    PerformanceNotifications.GenerationStarted,
    PerformanceNotifications.IrLoweringStarted
  ),
  postActions = setOf(
    PerformanceNotifications.IrGenerationFinished,
    PerformanceNotifications.GenerationFinished,
    CheckCompilationErrors.CheckDiagnosticCollector
  )
) {
  override fun executePhase(input: JvmFir2IrPipelineArtifact): JvmBinaryPipelineArtifact? {
    val (fir2IrResult, configuration, environment, diagnosticCollector) = input
    val project = environment.project
    val classResolver = FirJvmBackendClassResolver(fir2IrResult.components)
    val jvmBackendExtension = FirJvmBackendExtension(
      fir2IrResult.components,
      fir2IrResult.irActualizedResult?.actualizedExpectDeclarations?.extractFirDeclarations()
    )
    val baseBackendInput = fir2IrResult.toBackendInput(configuration, jvmBackendExtension)
    val codegenFactory = JvmIrCodegenFactory(configuration)

    val mapField = CompilerConfiguration::class.java.getDeclaredField("map")
    mapField.isAccessible = true

    val module = configuration.moduleChunk!!.modules.single()
    checkCancelled()
    val codegenInput = KotlinToJVMBytecodeCompiler.runLowerings(
      project = project,
      //configuration = configurationWithoutOutputDir,
      configuration = configuration,
      moduleDescriptor = fir2IrResult.irModuleFragment.descriptor,
      module = module,
      codegenFactory = codegenFactory,
      backendInput = baseBackendInput,
      diagnosticsReporter = diagnosticCollector,
      firJvmBackendClassResolver = classResolver,
      reportGenerationStarted = false,
    )

    checkCancelled()

    val generationState = KotlinToJVMBytecodeCompiler.runCodegen(
      codegenInput = codegenInput,
      state = codegenInput.state,
      codegenFactory = codegenFactory,
      diagnosticsReporter = diagnosticCollector,
      configuration = codegenInput.state.configuration,
      reportGenerationFinished = false,
    )

    checkCancelled()
    consumer(generationState.factory)

    return JvmBinaryPipelineArtifact(listOf(generationState))
  }
}
