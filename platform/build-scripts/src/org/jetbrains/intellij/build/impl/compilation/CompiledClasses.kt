// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import org.jetbrains.intellij.build.impl.JpsCompilationRunner
import org.jetbrains.intellij.build.impl.cleanOutput
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.nio.file.Path
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

internal object CompiledClasses {
  fun checkOptions(context: CompilationContext) {
    val options = context.options
    val messages = context.messages
    if (options.useCompiledClassesFromProjectOutput && options.incrementalCompilation) {
      val message = "'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, " +
                    "so 'incremental compilation' option cannot be enabled"
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
      messages.error("'${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE}' is specified, " +
                     "so 'incremental compilation' option cannot be enabled")
    }
    if (options.pathToCompiledClassesArchive != null && options.useCompiledClassesFromProjectOutput) {
      messages.error("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, " +
                     "so '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE}' cannot be used")
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && options.incrementalCompilation) {
      messages.error("'${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA}' is specified, " +
                       "so 'incremental compilation' option cannot be used")
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && options.useCompiledClassesFromProjectOutput) {
      messages.error("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, " +
                     "so '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA}' cannot be used to fetch compile output")
    }
    if (options.pathToCompiledClassesArchive != null && options.pathToCompiledClassesArchivesMetadata != null) {
      messages.error("'${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE}' is specified, " +
                     "so '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA}' cannot be used to fetch compile output")
    }
    if (options.forceRebuild && options.incrementalCompilation) {
      messages.warning("'${BuildOptions.FORCE_REBUILD_PROPERTY}' is specified, " +
                       "so 'incremental compilation' option will be ignored")
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

  @Synchronized
  fun reuseOrCompile(context: CompilationContext, moduleNames: Collection<String>? = null, includingTestsInModules: List<String>? = null) {
    val span = Span.current()
    when {
      context.options.useCompiledClassesFromProjectOutput -> {
        span.addEvent("compiled classes reused", Attributes.of(
          AttributeKey.stringKey("dir"), context.classesOutputDirectory.toString(),
        ))
      }
      context.options.pathToCompiledClassesArchive != null -> {
        span.addEvent("compilation skipped", Attributes.of(AttributeKey.stringKey("reuseFrom"),
                                                           context.options.pathToCompiledClassesArchive.toString()))
        unpackCompiledClasses(classOutput = context.classesOutputDirectory, context = context)
      }
      context.options.pathToCompiledClassesArchivesMetadata != null -> {
        span.addEvent("compilation skipped", Attributes.of(AttributeKey.stringKey("reuseFrom"),
                                                           context.options.pathToCompiledClassesArchivesMetadata.toString()))
        val forInstallers = System.getProperty("intellij.fetch.compiled.classes.for.installers", "false").toBoolean()
        fetchAndUnpackCompiledClasses(
          reportStatisticValue = context.messages::reportStatisticValue,
          withScope = { name, operation -> context.messages.block(name, operation) },
          classOutput = context.classesOutputDirectory,
          metadataFile = Path.of(context.options.pathToCompiledClassesArchivesMetadata!!),
          /**
           * [FetchAndUnpackItem.output].hash file shouldn't leak to installer distribution
           */
          saveHash = !forInstallers,
        )
      }
      PortableCompilationCache.IS_ENABLED -> {
        span.addEvent("JPS remote cache will be used for compilation")
        val jpsCache = context.portableCompilationCache
        jpsCache.downloadCacheAndCompileProject()
        jpsCache.upload()
      }
      else -> {
        compile(context, moduleNames, includingTestsInModules, isPortableCacheDownloaded = false)
        return
      }
    }
    context.options.useCompiledClassesFromProjectOutput = true
  }

  private fun unpackCompiledClasses(classOutput: Path, context: CompilationContext) {
    context.messages.block("unpack compiled classes archive") {
      NioFiles.deleteRecursively(classOutput)
      Decompressor.Zip(context.options.pathToCompiledClassesArchive ?: error("intellij.build.compiled.classes.archive is not set"))
        .extract(classOutput)
    }
  }


  fun isIncrementalCompilationDataAvailable(context: CompilationContext): Boolean {
    return context.options.incrementalCompilation &&
           context.compilationData.isIncrementalCompilationDataAvailable()
  }

  fun compile(
    context: CompilationContext,
    moduleNames: Collection<String>? = null,
    includingTestsInModules: List<String>? = null,
    isPortableCacheDownloaded: Boolean,
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
      context.messages.block(status) {
        if (isIncrementalCompilation) {
          // workaround for KT-55695
          runner.compileWithTimeout(
            context, moduleNames, includingTestsInModules,
            timeout = context.options.incrementalCompilationTimeout.minutes
          )
        }
        else {
          runner.compile(context, moduleNames, includingTestsInModules)
        }
      }
      context.messages.buildStatus(status)
    }
    catch (e: Exception) {
      retryCompilation(context, runner, moduleNames, includingTestsInModules, e)
    }
  }

  private fun JpsCompilationRunner.compileWithTimeout(
    context: CompilationContext,
    moduleNames: Collection<String>?,
    includingTestsInModules: List<String>?,
    timeout: Duration,
  ) {
    runBlocking {
      withTimeout(timeout) {
        launch {
          compile(
            context, moduleNames, includingTestsInModules,
            CanceledStatus { !isActive }
          )
        }
      }
    }
  }

  private fun retryCompilation(
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
      context.messages.warning("Incremental compilation failed. Not re-trying with clean build because " +
                               "'${BuildOptions.INCREMENTAL_COMPILATION_FALLBACK_REBUILD_PROPERTY}' is false.")
      throw e
    }
    var successMessage = "Clean build retry"
    when {
      e is TimeoutCancellationException -> {
        context.messages.reportBuildProblem("Incremental compilation timed out. Re-trying with clean build.")
        successMessage = "$successMessage after timeout"
        context.cleanOutput(keepCompilationState = false)
        context.options.incrementalCompilation = false
      }
      PortableCompilationCache.IS_ENABLED -> {
        successMessage = context.portableCompilationCache.handleCompilationFailureBeforeRetry(successMessage)
      }
      else -> {
        context.messages.warning("Incremental compilation failed. Re-trying with clean build.")
        context.cleanOutput(keepCompilationState = false)
        context.options.incrementalCompilation = false
      }
    }
    context.compilationData.reset()
    context.messages.block(successMessage) {
      runner.compile(context, moduleNames, includingTestsInModules)
    }
    context.messages.info("Compilation successful after clean build retry")
    context.messages.changeBuildStatusToSuccess(successMessage)
    context.messages.reportStatisticValue("Incremental compilation failures", "1")
  }

  private fun JpsCompilationRunner.compile(context: CompilationContext,
                                           moduleNames: Collection<String>?,
                                           includingTestsInModules: List<String>?,
                                           canceledStatus: CanceledStatus = CanceledStatus.NULL) {
    when {
      moduleNames != null -> buildModules(moduleNames.map(context::findRequiredModule), canceledStatus)
      includingTestsInModules != null -> buildProduction(canceledStatus)
      else -> {
        buildAll(canceledStatus)
        context.options.useCompiledClassesFromProjectOutput = true
      }
    }
    context.options.incrementalCompilation = true
    includingTestsInModules?.forEach {
      buildModuleTests(context.findRequiredModule(it), canceledStatus)
    }
  }
}