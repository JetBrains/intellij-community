// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("UnstableApiUsage")
package org.jetbrains.bazel.jvm.worker

import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.Tracer
import kotlinx.coroutines.job
import org.jetbrains.bazel.jvm.kotlin.NonIncrementalKotlinBuilder
import org.jetbrains.bazel.jvm.kotlin.JvmBuilderFlags
import org.jetbrains.bazel.jvm.use
import org.jetbrains.bazel.jvm.util.ArgMap
import org.jetbrains.bazel.jvm.worker.core.BazelBuildRootIndex
import org.jetbrains.bazel.jvm.worker.core.BazelBuildTargetIndex
import org.jetbrains.bazel.jvm.worker.core.BazelCompileContext
import org.jetbrains.bazel.jvm.worker.core.BazelCompileScope
import org.jetbrains.bazel.jvm.worker.core.BazelModuleBuildTarget
import org.jetbrains.bazel.jvm.worker.core.RequestLog
import org.jetbrains.bazel.jvm.worker.core.output.createOutputSink
import org.jetbrains.bazel.jvm.worker.core.output.writeJarAndAbi
import org.jetbrains.bazel.jvm.worker.impl.JpsTargetBuilder
import org.jetbrains.bazel.jvm.worker.impl.NoopIgnoredFileIndex
import org.jetbrains.bazel.jvm.worker.impl.NoopModuleExcludeIndex
import org.jetbrains.jps.builders.logging.BuildLoggingManager
import org.jetbrains.jps.cmdline.ProjectDescriptor
import org.jetbrains.jps.incremental.fs.BuildFSState
import org.jetbrains.jps.model.JpsModel
import java.nio.file.Path
import kotlin.coroutines.coroutineContext

internal suspend fun nonIncrementalBuildUsingJps(
  baseDir: Path,
  args: ArgMap<JvmBuilderFlags>,
  isDebugEnabled: Boolean,
  parentSpan: Span,
  tracer: Tracer,
  log: RequestLog,
  jpsModel: JpsModel,
  moduleTarget: BazelModuleBuildTarget,
): Int {
  val abiJar = args.optionalSingle(JvmBuilderFlags.ABI_OUT)?.let { baseDir.resolve(it).normalize() }
  val outJar = baseDir.resolve(args.mandatorySingle(JvmBuilderFlags.OUT)).normalize()
  if (isDebugEnabled) {
    parentSpan.setAttribute("outJar", outJar.toString())
    parentSpan.setAttribute("abiJar", abiJar?.toString() ?: "")
  }

  val projectDescriptor = ProjectDescriptor(
    /* model = */ jpsModel,
    /* fsState = */ BuildFSState(/* alwaysScanFS = */ true),
    /* dataManager = */
    null,
    /* loggingManager = */ BuildLoggingManager.DEFAULT,
    /* moduleExcludeIndex = */ NoopModuleExcludeIndex,
    /* buildTargetIndex = */ BazelBuildTargetIndex(moduleTarget),
    /* buildRootIndex = */ BazelBuildRootIndex(moduleTarget),
    /* ignoredFileIndex = */ NoopIgnoredFileIndex,
  )

  val context = BazelCompileContext(
    scope = BazelCompileScope(isIncrementalCompilation = false, isRebuild = true),
    projectDescriptor = projectDescriptor,
    delegateMessageHandler = log,
    coroutineContext = coroutineContext,
  )

  // non-incremental build - oldJar is always as null (no need to copy old unchanged files)
  createOutputSink(oldJar = null, oldAbiJar = null, withAbi = abiJar != null).use { outputSink ->
    val exitCode = tracer.spanBuilder("compile").use { span ->
      val builders = arrayOf(
        createJavaBuilder(
          tracer = tracer,
          isDebugEnabled = isDebugEnabled,
          out = log.out,
          javaFileCount = moduleTarget.javaFileCount,
          isIncremental = false,
        ),
        //NotNullInstrumentingBuilder(),
        NonIncrementalKotlinBuilder(job = coroutineContext.job, span = span),
      ).filterNotNull().toTypedArray()
      builders.sortBy { it.category.ordinal }

      JpsTargetBuilder(
        log = log,
        dataManager = null,
        tracer = tracer,
      ).build(
        context = context,
        moduleTarget = moduleTarget,
        builders = builders,
        buildState = null,
        outputSink = outputSink,
        parentSpan = span,
        dependencyAnalyzer = null,
      )
    }
    if (exitCode == 0) {
      writeJarAndAbi(tracer = tracer, outputSink = outputSink, outJar = outJar, abiJar = abiJar)
    }
    return exitCode
  }
}


