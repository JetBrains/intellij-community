// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.kotlin

import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.jetbrains.bazel.jvm.kotlin.configureModule
import org.jetbrains.bazel.jvm.kotlin.createJvmPipeline
import org.jetbrains.bazel.jvm.kotlin.executeJvmPipeline
import org.jetbrains.bazel.jvm.kotlin.prepareCompilerConfiguration
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.bazel.jvm.worker.core.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.worker.core.BazelDirtyFileHolder
import org.jetbrains.bazel.jvm.worker.core.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.worker.core.BazelTargetBuildOutputConsumer
import org.jetbrains.bazel.jvm.worker.core.BazelTargetBuilder
import org.jetbrains.bazel.jvm.worker.core.output.OutputSink
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.progress.CompilationCanceledStatus
import kotlin.coroutines.coroutineContext

class NonIncrementalKotlinBuilder(
  private val job: Job,
  private val span: Span,
) : BazelTargetBuilder(BuilderCategory.SOURCE_PROCESSOR) {
  override fun getPresentableName() = "Kotlin Non-Incremental Builder"

  override fun getCompilableFileExtensions() = arrayListOf("kt")

  override suspend fun build(
    context: BazelCompileContext,
    module: JpsModule,
    chunk: ModuleChunk,
    target: BazelModuleBuildTarget,
    dirtyFilesHolder: BazelDirtyFileHolder,
    outputConsumer: BazelTargetBuildOutputConsumer,
    outputSink: OutputSink
  ): ExitCode {
    val messageCollector = MessageCollectorAdapter(
      context = context,
      span = span,
      kotlinTarget = null,
      skipWarns = target.module.container.getChild(BazelConfigurationHolder.KIND)!!.kotlinArgs.let { it.suppressWarnings && !it.allWarningsAsErrors },
    )
    val builder = Services.Builder()
    builder.register(CompilationCanceledStatus::class.java, object : CompilationCanceledStatus {
      override fun checkCanceled() {
        job.ensureActive()
      }
    })

    val bazelConfigurationHolder = module.container.getChild(BazelConfigurationHolder.KIND)!!
    val config = prepareCompilerConfiguration(
      args = bazelConfigurationHolder.args,
      kotlinArgs = bazelConfigurationHolder.kotlinArgs,
      baseDir = bazelConfigurationHolder.classPathRootDir,
      abiOutputConsumer = {
        outputConsumer.registerKotlincAbiOutput(it)
      },
    )
    configureModule(
      moduleName = module.name,
      config = config,
      outFileOrDirPath = "",
      args = bazelConfigurationHolder.args,
      baseDir = bazelConfigurationHolder.classPathRootDir,
      allSources = bazelConfigurationHolder.sources,
      changedKotlinSources = null,
      classPath = bazelConfigurationHolder.classPath,
    )

    val coroutineContext = coroutineContext
    val pipeline = createJvmPipeline(config, checkCancelled = { coroutineContext.ensureActive() }) {
      outputConsumer.registerKotlincOutput(context, it.asList())
    }
    val exitCode = executeJvmPipeline(pipeline, bazelConfigurationHolder.kotlinArgs, builder.build(), messageCollector)
    if (exitCode == org.jetbrains.kotlin.cli.common.ExitCode.INTERNAL_ERROR) {
      context.compilerMessage(kind = BuildMessage.Kind.ERROR, message = "Internal compiler error")
      return ExitCode.ABORT
    }

    if (Utils.ERRORS_DETECTED_KEY.get(context, false)) {
      JavaBuilderUtil.registerFilesWithErrors(context, messageCollector.filesWithErrors.map { it.toFile() })
      return ExitCode.ABORT
    }
    else {
      return ExitCode.OK
    }
  }
}