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
import org.jetbrains.jps.api.CanceledStatus
import java.nio.file.Path
import kotlin.time.Duration.Companion.minutes

internal object CompiledClasses {
  fun checkOptions(context: CompilationContext) {
    val options = context.options
    val messages = context.messages
    if (options.useCompiledClassesFromProjectOutput && options.incrementalCompilation) {
      messages.warning("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, " +
                       "so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchive != null && PortableCompilationCache.IS_ENABLED) {
      messages.warning("JPS Cache is enabled so the archive with compiled project output won't be used")
      options.pathToCompiledClassesArchive = null
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && PortableCompilationCache.IS_ENABLED) {
      messages.warning("JPS Cache is enabled " +
                       "so the archive with the compiled project output metadata won't be used to fetch compile output")
      options.pathToCompiledClassesArchivesMetadata = null
    }
    if (options.pathToCompiledClassesArchive != null && options.incrementalCompilation) {
      messages.warning("Paths to the compiled project output is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchive != null && options.useCompiledClassesFromProjectOutput) {
      messages.warning("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, " +
                       "so the archive with compiled project output won't be used")
      options.pathToCompiledClassesArchive = null
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && options.incrementalCompilation) {
      messages.warning("Paths to the compiled project output metadata is specified, so 'incremental compilation' option will be ignored")
      options.incrementalCompilation = false
    }
    if (options.pathToCompiledClassesArchivesMetadata != null && options.useCompiledClassesFromProjectOutput) {
      messages.warning("'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, " +
                       "so the archive with the compiled project output metadata won't be used to fetch compile output")
      options.pathToCompiledClassesArchivesMetadata = null
    }
    if (!options.useCompiledClassesFromProjectOutput) {
      messages.info("Incremental compilation: ${options.incrementalCompilation}")
    }
  }

  /**
   * @return true even if [PortableCompilationCache.IS_ENABLED] because incremental compilation
   * may still be triggered due to [PortableCompilationCache.isCompilationRequired]
   */
  fun isCompilationRequired(options: BuildOptions): Boolean {
    return !options.useCompiledClassesFromProjectOutput &&
           options.pathToCompiledClassesArchive == null &&
           options.pathToCompiledClassesArchivesMetadata == null
  }

  fun keepCompilationState(options: BuildOptions): Boolean {
    return PortableCompilationCache.IS_ENABLED ||
           options.useCompiledClassesFromProjectOutput ||
           options.pathToCompiledClassesArchive == null ||
           options.pathToCompiledClassesArchivesMetadata != null ||
           options.incrementalCompilation
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
        val jpsCache = PortableCompilationCache(context)
        jpsCache.downloadCacheAndCompileProject()
        jpsCache.upload()
      }
      else -> {
        if (context.options.incrementalCompilation) {
          span.addEvent("reusing locally available compilation state if any")
        }
        else {
          span.addEvent("no compiled classes can be reused")
        }
        compileLocally(context, moduleNames, includingTestsInModules)
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

  private fun compileLocally(context: CompilationContext,
                             moduleNames: Collection<String>? = null,
                             includingTestsInModules: List<String>? = null) {
    check(JavaVersion.current().isAtLeast(17)) {
      "Build script must be executed under Java 17 to compile intellij project but it's executed under Java ${JavaVersion.current()}"
    }
    check(isCompilationRequired(context.options)) {
      "Unexpected compilation request, unable to proceed"
    }
    context.messages.progress("Compiling project")
    context.compilationData.statisticsReported = false
    val runner = JpsCompilationRunner(context)
    val isIncrementalCompilationDataAvailable = context.options.incrementalCompilation &&
                                                context.compilationData.isIncrementalCompilationDataAvailable()
    try {
      val status = when {
        isIncrementalCompilationDataAvailable -> "Compiled using local cache"
        else -> "Clean build"
      }
      context.messages.block(status) {
        if (isIncrementalCompilationDataAvailable) runBlocking {
          // workaround for KT-55695
          withTimeout(context.options.incrementalCompilationTimeout.minutes) {
            launch {
              runner.compile(
                context, moduleNames, includingTestsInModules,
                CanceledStatus { !isActive }
              )
            }
          }
        }
        else runner.compile(context, moduleNames, includingTestsInModules)
      }
      context.messages.buildStatus(status)
    }
    catch (e: Exception) {
      if (!context.options.incrementalCompilation) {
        throw e
      }
      if (!context.options.incrementalCompilationFallbackRebuild) {
        context.messages.warning("Incremental compilation failed. Not re-trying with clean build because " +
                                 "'${BuildOptions.INCREMENTAL_COMPILATION_FALLBACK_REBUILD_PROPERTY}' is false.")
        throw e
      }
      if (e is TimeoutCancellationException) {
        context.messages.reportBuildProblem("Incremental compilation timed out. Re-trying with clean build.")
      }
      else {
        context.messages.warning("Incremental compilation failed. Re-trying with clean build.")
      }
      context.options.incrementalCompilation = false
      context.compilationData.reset()
      context.messages.block("Clean build retry") {
        runner.compile(context, moduleNames, includingTestsInModules)
      }
      context.messages.info("Compilation successful after clean build retry")
      context.messages.changeBuildStatusToSuccess("Clean build retry")
      context.messages.reportStatisticValue("Incremental compilation failures", "1")
    }
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