// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral", "DialogTitleCapitalization", "UnstableApiUsage", "ReplaceGetOrSet")

package org.jetbrains.bazel.jvm.jps.kotlin

import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import org.jetbrains.bazel.jvm.jps.BazelConfigurationHolder
import org.jetbrains.bazel.jvm.jps.OutputSink
import org.jetbrains.bazel.jvm.jps.impl.BazelCompileContext
import org.jetbrains.bazel.jvm.jps.impl.BazelDirtyFileHolder
import org.jetbrains.bazel.jvm.jps.impl.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.jps.impl.BazelTargetBuildOutputConsumer
import org.jetbrains.bazel.jvm.jps.impl.BazelTargetBuilder
import org.jetbrains.bazel.jvm.kotlin.configureModule
import org.jetbrains.bazel.jvm.kotlin.createJvmPipeline
import org.jetbrains.bazel.jvm.kotlin.executeJvmPipeline
import org.jetbrains.bazel.jvm.kotlin.prepareCompilerConfiguration
import org.jetbrains.jps.ModuleChunk
import org.jetbrains.jps.builders.java.JavaBuilderUtil
import org.jetbrains.jps.incremental.BuilderCategory
import org.jetbrains.jps.incremental.Utils
import org.jetbrains.jps.incremental.messages.BuildMessage
import org.jetbrains.jps.model.module.JpsModule
import org.jetbrains.kotlin.config.Services
import org.jetbrains.kotlin.progress.CompilationCanceledStatus

internal class NonIncrementalKotlinBuilder(
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
    )
    configureModule(
      moduleName = module.name,
      config = config,
      outFileOrDirPath = "",
      args = bazelConfigurationHolder.args,
      baseDir = bazelConfigurationHolder.classPathRootDir,
      allSources = bazelConfigurationHolder.sources,
      changedKotlinSources = null,
      classPath = bazelConfigurationHolder.classPath.asList(),
    )

    val pipeline = createJvmPipeline(config) {
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