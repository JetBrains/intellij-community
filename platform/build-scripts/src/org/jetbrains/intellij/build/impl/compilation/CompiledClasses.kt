// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import com.intellij.util.lang.JavaVersion
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.JpsCompilationRunner
import org.jetbrains.intellij.build.impl.cleanOutput
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.nio.file.Path
import java.util.concurrent.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal object CompiledClasses {
  fun checkOptions(context: CompilationContext) {
    val options = context.options
    val messages = context.messages
    if (options.useCompiledClassesFromProjectOutput && options.incrementalCompilation) {
      val message = "'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, so 'incremental compilation' option cannot be enabled"
      if (options.isInDevelopmentMode) {
        messages.warning(message)
        options.incrementalCompilation = false
      }
      else {
        messages.error(message)
      }
    }
    if (options.pathToCompiledClassesArchive != null && PortableCompilationCache.IS_ENABLED) {
      messages.error("JPS Cache is enabled so '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE}' cannot be used")
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && PortableCompilationCache.IS_ENABLED) {
      messages.error("JPS Cache is enabled " +
                     "so '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA}' cannot be used to fetch compile output")
    }
    if (options.pathToCompiledClassesArchive != null && options.incrementalCompilation) {
      messages.error("'${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE}' is specified, so 'incremental compilation' option cannot be enabled")
    }

    if (options.useCompiledClassesFromProjectOutput) {
      if (options.pathToCompiledClassesArchive != null) {
        messages.error(
          "'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, " +
          "so '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE}' cannot be used"
        )
      }
      if (options.pathToCompiledClassesArchivesMetadata != null) {
        messages.error(
          "'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, " +
          "so '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA}' cannot be used to fetch compile output"
        )
      }
    }

    if (options.pathToCompiledClassesArchivesMetadata != null && options.incrementalCompilation) {
      messages.error("'${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA}' is specified, " +
                       "so 'incremental compilation' option cannot be used")
    }

    if (options.pathToCompiledClassesArchive != null && options.pathToCompiledClassesArchivesMetadata != null) {
      messages.error("'${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE}' is specified, " +
                     "so '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA}' cannot be used to fetch compile output")
    }
    if (options.forceRebuild && options.incrementalCompilation) {
      messages.warning("'${BuildOptions.FORCE_REBUILD_PROPERTY}' is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.forceRebuild && options.useCompiledClassesFromProjectOutput) {
      val message = "Both '${BuildOptions.FORCE_REBUILD_PROPERTY}' and '${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' options are specified"
      if (options.isInDevelopmentMode) {
        messages.warning(message)
        options.incrementalCompilation = false
      }
      else {
        messages.error(message)
      }
    }
    if (options.forceRebuild && context.options.pathToCompiledClassesArchive != null) {
      messages.error(
        "Both '${BuildOptions.FORCE_REBUILD_PROPERTY}' and '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE}' options are specified"
      )
    }
    if (options.forceRebuild && context.options.pathToCompiledClassesArchivesMetadata != null) {
      messages.error(
        "Both '${BuildOptions.FORCE_REBUILD_PROPERTY}' and '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA}' options are specified"
      )
    }
    if (options.isInDevelopmentMode && ProjectStamps.PORTABLE_CACHES) {
      messages.error(
        "${ProjectStamps.PORTABLE_CACHES_PROPERTY} is not expected to be enabled in development mode due to performance penalty"
      )
    }
    if (!options.useCompiledClassesFromProjectOutput) {
      messages.info("Incremental compilation: ${options.incrementalCompilation}")
    }
  }

  fun isCompilationRequired(options: BuildOptions): Boolean {
    return options.forceRebuild ||
           !options.useCompiledClassesFromProjectOutput &&
           options.pathToCompiledClassesArchive == null &&
           options.pathToCompiledClassesArchivesMetadata == null
  }

  fun keepCompilationState(options: BuildOptions): Boolean {
    return !options.forceRebuild &&
           (PortableCompilationCache.IS_ENABLED ||
            options.useCompiledClassesFromProjectOutput ||
            options.pathToCompiledClassesArchive == null ||
            options.pathToCompiledClassesArchivesMetadata != null ||
            options.incrementalCompilation)
  }

  suspend fun reuseOrCompile(context: CompilationContext, moduleNames: Collection<String>? = null, includingTestsInModules: List<String>? = null) {
    val span = Span.current()
    when {
      context.options.useCompiledClassesFromProjectOutput -> {
        span.addEvent("compiled classes reused", Attributes.of(AttributeKey.stringKey("dir"), context.classesOutputDirectory.toString()))
      }
      context.options.pathToCompiledClassesArchive != null -> {
        span.addEvent("compilation skipped", Attributes.of(AttributeKey.stringKey("reuseFrom"), context.options.pathToCompiledClassesArchive.toString()))
        unpackCompiledClasses(classOutput = context.classesOutputDirectory, context = context)
      }
      context.options.pathToCompiledClassesArchivesMetadata != null -> {
        span.addEvent("compilation skipped", Attributes.of(AttributeKey.stringKey("reuseFrom"), context.options.pathToCompiledClassesArchivesMetadata.toString()))
        val forInstallers = System.getProperty("intellij.fetch.compiled.classes.for.installers", "false").toBoolean()
        spanBuilder("fetch and unpack compiled classes").use {
          fetchAndUnpackCompiledClasses(
            reportStatisticValue = context.messages::reportStatisticValue,
            classOutput = context.classesOutputDirectory,
            metadataFile = Path.of(context.options.pathToCompiledClassesArchivesMetadata!!),
            skipUnpack = !context.options.unpackCompiledClassesArchives,
            /**
             * [FetchAndUnpackItem.output].hash files shouldn't leak to installer distribution
             */
            saveHash = !forInstallers,
          )
        }
      }
      PortableCompilationCache.IS_ENABLED -> {
        span.addEvent("JPS remote cache will be used for compilation")
        runBlocking(Dispatchers.Default) {
          context.portableCompilationCache.downloadCacheAndCompileProject()
        }
      }
      else -> {
        compile(moduleNames = moduleNames, includingTestsInModules = includingTestsInModules, isPortableCacheDownloaded = false, context = context)
        return
      }
    }
    if (context.options.useCompiledClassesFromProjectOutput) {
      context.compilationData.runtimeModuleRepositoryGenerated = true
    }
    else {
      CompilationTasks.create(context).generateRuntimeModuleRepository()
      context.options.useCompiledClassesFromProjectOutput = true
    }
  }

  private suspend fun unpackCompiledClasses(classOutput: Path, context: CompilationContext) {
    spanBuilder("unpack compiled classes archive").use {
      NioFiles.deleteRecursively(classOutput)
      Decompressor.Zip(context.options.pathToCompiledClassesArchive ?: error("intellij.build.compiled.classes.archive is not set"))
        .extract(classOutput)
    }
  }


  fun isIncrementalCompilationDataAvailable(context: CompilationContext): Boolean {
    return context.options.incrementalCompilation &&
           context.compilationData.isIncrementalCompilationDataAvailable()
  }

  suspend fun compile(
    moduleNames: Collection<String>? = null,
    includingTestsInModules: List<String>? = null,
    isPortableCacheDownloaded: Boolean,
    context: CompilationContext,
  ) {
    check(JavaVersion.current().isAtLeast(17)) {
      "Build script must be executed under Java 17 to compile intellij project but it's executed under Java ${JavaVersion.current()}"
    }
    check(isCompilationRequired(context.options)) {
      "Unexpected compilation request, unable to proceed"
    }
    context.messages.progress("Compiling project")
    context.compilationData.statisticsReported = false
    val runner = JpsCompilationRunner(context)
    try {
      val (status, isIncrementalCompilation) = when {
        context.options.forceRebuild -> "Forced rebuild" to false
        isPortableCacheDownloaded -> context.portableCompilationCache.usageStatus() to true
        isIncrementalCompilationDataAvailable(context) -> "Compiled using local cache" to true
        else -> "Clean build" to false
      }
      context.options.incrementalCompilation = isIncrementalCompilation
      if (!isIncrementalCompilation) {
        Span.current().addEvent("no compiled classes can be reused")
      }
      spanBuilder(status).use {
        if (isIncrementalCompilation) {
          // workaround for KT-55695
          compileWithTimeout(
            compilationRunner = runner,
            context = context,
            moduleNames = moduleNames,
            includingTestsInModules = includingTestsInModules,
            timeout = context.options.incrementalCompilationTimeout.minutes
          )
        }
        else {
          compile(jpsCompilationRunner = runner, context = context, moduleNames = moduleNames, includingTestsInModules = includingTestsInModules)
        }
      }
      context.messages.buildStatus(status)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      retryCompilation(context = context, runner = runner, moduleNames = moduleNames, includingTestsInModules = includingTestsInModules, e = e)
    }
  }

  private suspend fun compileWithTimeout(
    compilationRunner: JpsCompilationRunner,
    context: CompilationContext,
    moduleNames: Collection<String>?,
    includingTestsInModules: List<String>?,
    timeout: Duration,
  ) {
    withTimeout(timeout) {
      launch {
        compile(
          jpsCompilationRunner = compilationRunner,
          context = context,
          moduleNames = moduleNames,
          includingTestsInModules = includingTestsInModules,
          canceledStatus = CanceledStatus { !isActive },
        )
      }
    }
  }

  private suspend fun retryCompilation(
    context: CompilationContext,
    runner: JpsCompilationRunner,
    moduleNames: Collection<String>?,
    includingTestsInModules: List<String>?,
    e: Exception
  ) {
    if (!context.options.incrementalCompilation) {
      throw e
    }
    if (!context.options.incrementalCompilationFallbackRebuild) {
      Span.current().addEvent("Incremental compilation failed. Not re-trying with clean build because " +
                               "'${BuildOptions.INCREMENTAL_COMPILATION_FALLBACK_REBUILD_PROPERTY}' is false.")
      throw e
    }
    var successMessage = "Clean build retry"
    when {
      e is TimeoutCancellationException -> {
        context.messages.reportBuildProblem("Incremental compilation timed out. Re-trying with clean build.")
        successMessage = "$successMessage after timeout"
        cleanOutput(compilationContext = context, keepCompilationState = false)
        context.options.incrementalCompilation = false
      }
      PortableCompilationCache.IS_ENABLED -> {
        successMessage = context.portableCompilationCache.handleCompilationFailureBeforeRetry(successMessage)
      }
      else -> {
        Span.current().addEvent("Incremental compilation failed. Re-trying with clean build.")
        cleanOutput(compilationContext = context, keepCompilationState = false)
        context.options.incrementalCompilation = false
      }
    }
    context.compilationData.reset()
    spanBuilder(successMessage).use {
      compile(jpsCompilationRunner = runner, context = context, moduleNames = moduleNames, includingTestsInModules = includingTestsInModules)
    }
    Span.current().addEvent("Compilation successful after clean build retry")
    context.messages.changeBuildStatusToSuccess(successMessage)
    context.messages.reportStatisticValue("Incremental compilation failures", "1")
  }

  private suspend fun compile(
    jpsCompilationRunner: JpsCompilationRunner,
    context: CompilationContext,
    moduleNames: Collection<String>?,
    includingTestsInModules: List<String>?,
    canceledStatus: CanceledStatus = CanceledStatus.NULL
  ) {
    when {
      moduleNames != null -> jpsCompilationRunner.buildModules(moduleNames.map(context::findRequiredModule), canceledStatus)
      includingTestsInModules != null -> jpsCompilationRunner.buildProduction(canceledStatus)
      else -> {
        jpsCompilationRunner.buildAll(canceledStatus)
        context.options.useCompiledClassesFromProjectOutput = true
      }
    }
    context.options.incrementalCompilation = true
    includingTestsInModules?.forEach {
      jpsCompilationRunner.buildModuleTests(context.findRequiredModule(it), canceledStatus)
    }
  }
}