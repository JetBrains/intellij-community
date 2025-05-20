// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.tools.build.bazel.jvmIncBuilder.impl

import org.jetbrains.kotlin.backend.common.output.OutputFileCollection
import org.jetbrains.kotlin.backend.common.phaser.then
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.pipeline.*
import org.jetbrains.kotlin.cli.pipeline.jvm.*
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.phaser.CompilerPhase
import org.jetbrains.kotlin.platform.jvm.JvmPlatforms
import org.jetbrains.kotlin.util.PerformanceManager
import org.jetbrains.kotlin.util.PerformanceManagerImpl

private class BazelJvmCliPipeline(
  private val configurationUpdater: (CompilerConfiguration) -> Unit,
  private val outputConsumer: (OutputFileCollection) -> Unit,
) : AbstractCliPipeline<K2JVMCompilerArguments>() {
  override val defaultPerformanceManager: PerformanceManager
    get() = PerformanceManagerImpl(JvmPlatforms.defaultJvmPlatform, "")

  override fun createCompoundPhase(arguments: K2JVMCompilerArguments): CompilerPhase<PipelineContext, ArgumentsPipelineArtifact<K2JVMCompilerArguments>, *> {
    return createRegularPipeline()
  }

  private fun createRegularPipeline(): CompilerPhase<PipelineContext, ArgumentsPipelineArtifact<K2JVMCompilerArguments>, ExitCodeArtifact> {
    return JvmConfigurationPipelinePhase then
      BazelConfigurationUpdaterPipelinePhase(configurationUpdater) then
      JvmFrontendPipelinePhase then
      JvmFir2IrPipelinePhase then
      JvmBackendPipelinePhase then
      BazelJvmBackendPipelinePhase(outputConsumer)
  }
}

class BazelJvmBackendPipelinePhase(
    private val consumer: (OutputFileCollection) -> Unit,
) :PipelinePhase<JvmBackendPipelineArtifact, ExitCodeArtifact>(
  name = "BazelJvmBackendPipelinePhase",
) {
  override fun executePhase(input: JvmBackendPipelineArtifact): ExitCodeArtifact {
    consumer(input.outputs.firstOrNull()!!.factory)
    return ExitCodeArtifact(ExitCode.OK)
  }

}

class ExitCodeArtifact(override val exitCode: ExitCode): PipelineArtifactWithExitCode()

class BazelConfigurationUpdaterPipelinePhase(private val updater: (CompilerConfiguration) -> Unit) : PipelinePhase<ConfigurationPipelineArtifact, ConfigurationPipelineArtifact>(
  name = "BazelConfigurationUpdaterPipelinePhase",
) {
  override fun executePhase(input: ConfigurationPipelineArtifact): ConfigurationPipelineArtifact? {
    val configurationPipelineArtifact = input
    val configuration = input.configuration
    updater(configuration)
    return configurationPipelineArtifact
  }
}