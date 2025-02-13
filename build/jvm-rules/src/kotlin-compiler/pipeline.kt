// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.jvm.kotlin

import com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.cli.common.CLICompiler
import org.jetbrains.kotlin.cli.common.ExitCode
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.GroupingMessageCollector
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.cli.pipeline.AbstractCliPipeline
import org.jetbrains.kotlin.cli.pipeline.ArgumentsPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.CheckCompilationErrors
import org.jetbrains.kotlin.cli.pipeline.PipelineArtifactWithExitCode
import org.jetbrains.kotlin.cli.pipeline.PipelineContext
import org.jetbrains.kotlin.cli.pipeline.PipelineStepException
import org.jetbrains.kotlin.cli.pipeline.SuccessfulPipelineExecutionException
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.config.phaser.PhaseConfig
import org.jetbrains.kotlin.config.phaser.invokeToplevel

fun executeJvmPipeline(
  pipeline: AbstractCliPipeline<K2JVMCompilerArguments>,
  args: K2JVMCompilerArguments,
  services: Services,
  messageCollector: MessageCollector,
): ExitCode {
  val rootDisposable = Disposer.newDisposable("Disposable for ${CLICompiler::class.simpleName}.execImpl")
  val messageCollector = GroupingMessageCollector(messageCollector, false, false)
  val argumentsInput = ArgumentsPipelineArtifact(
    args,
    services,
    rootDisposable,
    messageCollector,
    K2JVMCompiler.K2JVMCompilerPerformanceManager(),
  )

  try {
    val code = runPhasedPipeline(argumentsInput, pipeline)
    return if (messageCollector.hasErrors()) ExitCode.COMPILATION_ERROR else code
  }
  finally {
    messageCollector.flush()
    Disposer.dispose(rootDisposable)
  }
}

private fun runPhasedPipeline(
  input: ArgumentsPipelineArtifact<K2JVMCompilerArguments>,
  pipeline: AbstractCliPipeline<K2JVMCompilerArguments>,
): ExitCode {
  val compoundPhase = pipeline.createCompoundPhase(input.arguments)

  val phaseConfig = PhaseConfig()
  val context = PipelineContext(
    messageCollector = input.messageCollector,
    diagnosticCollector = input.diagnosticCollector,
    performanceManager = input.performanceManager,
    renderDiagnosticInternalName = input.arguments.renderInternalDiagnosticNames,
    kaptMode = false,
  )
  try {
    val result = compoundPhase.invokeToplevel(phaseConfig = phaseConfig, context = context, input = input)
    return if (result is PipelineArtifactWithExitCode) result.exitCode else ExitCode.OK
  }
  catch (e: PipelineStepException) {
    /**
     * There might be a case when the pipeline is not executed fully, but it's not considered as a compilation error:
     *   if `-version` flag was passed
     */
    return if (e.definitelyCompilationError || input.messageCollector.hasErrors() || input.diagnosticCollector.hasErrors) {
      ExitCode.COMPILATION_ERROR
    }
    else {
      ExitCode.OK
    }
  }
  catch (_: SuccessfulPipelineExecutionException) {
    return ExitCode.OK
  }
  finally {
    CheckCompilationErrors.CheckDiagnosticCollector.reportDiagnosticsToMessageCollector(context)
  }
}
