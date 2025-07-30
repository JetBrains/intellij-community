// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.io.NioFiles
import com.intellij.util.io.Decompressor
import com.intellij.util.currentJavaVersion
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withTimeout
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.JpsCompilationRunner
import org.jetbrains.intellij.build.impl.cleanOutput
import org.jetbrains.intellij.build.impl.generateRuntimeModuleRepository
import org.jetbrains.intellij.build.impl.isBazelTestRun
import org.jetbrains.intellij.build.jpsCache.isForceDownloadJpsCache
import org.jetbrains.intellij.build.jpsCache.isPortableCompilationCacheEnabled
import org.jetbrains.intellij.build.jpsCache.jpsCacheRemoteGitUrl
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.nio.file.Path
import kotlin.io.path.exists

internal fun checkCompilationOptions(context: CompilationContext) {
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
  if (options.pathToCompiledClassesArchive != null && isPortableCompilationCacheEnabled) {
    messages.error("JPS Cache is enabled so '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE}' cannot be used")
  }
  val pathToCompiledClassArchiveMetadata = options.pathToCompiledClassesArchivesMetadata
  if (pathToCompiledClassArchiveMetadata != null && isPortableCompilationCacheEnabled) {
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
    if (pathToCompiledClassArchiveMetadata != null) {
      messages.error(
        "'${BuildOptions.USE_COMPILED_CLASSES_PROPERTY}' is specified, " +
        "so '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA}' cannot be used to fetch compile output"
      )
    }
  }

  if (pathToCompiledClassArchiveMetadata != null && options.incrementalCompilation) {
    messages.error("'${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA}' is specified, " +
                     "so 'incremental compilation' option cannot be used")
  }

  if (options.pathToCompiledClassesArchive != null && pathToCompiledClassArchiveMetadata != null) {
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
      Span.current().addEvent(message)
      options.incrementalCompilation = false
    }
    else {
      messages.error(message)
    }
  }
  if (options.forceRebuild && options.pathToCompiledClassesArchive != null) {
    messages.error("Both '${BuildOptions.FORCE_REBUILD_PROPERTY}' and '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVE}' options are specified")
  }
  if (options.forceRebuild && pathToCompiledClassArchiveMetadata != null) {
    messages.error("Both '${BuildOptions.FORCE_REBUILD_PROPERTY}' and '${BuildOptions.INTELLIJ_BUILD_COMPILER_CLASSES_ARCHIVES_METADATA}' options are specified")
  }
  if (options.isInDevelopmentMode && ProjectStamps.PORTABLE_CACHES && !System.getProperty("jps.cache.test").toBoolean()) {
    messages.error("${ProjectStamps.PORTABLE_CACHES_PROPERTY} is not expected to be enabled in development mode due to performance penalty")
  }
  if (!options.useCompiledClassesFromProjectOutput) {
    Span.current().addEvent("incremental compilation", Attributes.of(AttributeKey.booleanKey("options.incrementalCompilation"), options.incrementalCompilation))
  }
}

internal fun isCompilationRequired(options: BuildOptions): Boolean {
  return options.forceRebuild ||
         !options.useCompiledClassesFromProjectOutput &&
         options.pathToCompiledClassesArchive == null &&
         options.pathToCompiledClassesArchivesMetadata == null
}

internal fun keepCompilationState(options: BuildOptions): Boolean {
  return !options.forceRebuild &&
         (isPortableCompilationCacheEnabled ||
          options.useCompiledClassesFromProjectOutput ||
          options.pathToCompiledClassesArchive == null ||
          options.pathToCompiledClassesArchivesMetadata != null ||
          options.incrementalCompilation)
}

internal suspend fun reuseOrCompile(context: CompilationContext, moduleNames: Collection<String>?, includingTestsInModules: List<String>?, span: Span) {
  val pathToCompiledClassArchiveMetadata = context.options.pathToCompiledClassesArchivesMetadata
  when {
    context.options.useCompiledClassesFromProjectOutput -> {
      check(isBazelTestRun() || context.classesOutputDirectory.exists()) {
        "${BuildOptions.USE_COMPILED_CLASSES_PROPERTY} is enabled but the classes output directory ${context.classesOutputDirectory} doesn't exist"
      }
      val production = context.classesOutputDirectory.resolve("production")
      if (!production.exists()) {
        val msg = "${BuildOptions.USE_COMPILED_CLASSES_PROPERTY} is enabled but the classes output directory $production doesn't exist " +
                  "which may cause issues like 'Error: Could not find or load main class'"
        context.messages.warning(msg)
        span.addEvent(msg)
      }
      span.addEvent("compiled classes reused", Attributes.of(AttributeKey.stringKey("dir"), context.classesOutputDirectory.toString()))
    }
    context.options.pathToCompiledClassesArchive != null -> {
      span.addEvent("compilation skipped", Attributes.of(AttributeKey.stringKey("reuseFrom"), context.options.pathToCompiledClassesArchive.toString()))
      unpackCompiledClasses(classOutput = context.classesOutputDirectory, context = context)
    }
    pathToCompiledClassArchiveMetadata != null -> {
      span.addEvent("compilation skipped", Attributes.of(AttributeKey.stringKey("reuseFrom"), pathToCompiledClassArchiveMetadata.toString()))
      val forInstallers = System.getProperty("intellij.fetch.compiled.classes.for.installers", "false").toBoolean()
      spanBuilder("fetch and unpack compiled classes").use {
        fetchAndUnpackCompiledClasses(
          reportStatisticValue = context.messages::reportStatisticValue,
          classOutput = context.classesOutputDirectory,
          metadataFile = pathToCompiledClassArchiveMetadata,
          skipUnpack = !context.options.unpackCompiledClassesArchives,
          /**
           * [FetchAndUnpackItem.output].hash files shouldn't leak to installer distribution
           */
          saveHash = !forInstallers,
        )
      }
    }
    else -> {
      var doCompileWithoutJpsCache = true
      if (isPortableCompilationCacheEnabled) {

        val forceDownload = isForceDownloadJpsCache
        val forceRebuild = context.options.forceRebuild

        val isLocalCacheUsed = !forceRebuild && !forceDownload && isIncrementalCompilationDataAvailable(context)
        val shouldBeDownloaded = !forceRebuild && !isLocalCacheUsed
        if (shouldBeDownloaded) {
          span.addEvent("JPS remote cache will be used for compilation")
          doCompileWithoutJpsCache = false
          downloadCacheAndCompileProject(
            forceDownload = isForceDownloadJpsCache,
            gitUrl = jpsCacheRemoteGitUrl,
            context = context,
          )
        }
        else {
          span.addEvent(
            "JPS remote cache will NOT be used for compilation",
            Attributes.of(
              AttributeKey.booleanKey("forceRebuild"), forceRebuild,
              AttributeKey.booleanKey("forceDownload"), forceDownload,
              AttributeKey.booleanKey("isLocalCacheUsed"), isLocalCacheUsed,
              AttributeKey.booleanKey("isIncrementalCompilationDataAvailable"), isIncrementalCompilationDataAvailable(context),
            ),
          )
        }
      }

      if (doCompileWithoutJpsCache) {
        spanBuilder("compile modules").use {
          doCompile(
            moduleNames = moduleNames,
            includingTestsInModules = includingTestsInModules,
            availableCommitDepth = -1,
            context = context,
            handleCompilationFailureBeforeRetry = null,
          )
        }
      }
      return
    }
  }

  if (context.options.useCompiledClassesFromProjectOutput) {
    context.compilationData.runtimeModuleRepositoryGenerated = true
  }
  else {
    generateRuntimeModuleRepository(context)
    context.options.useCompiledClassesFromProjectOutput = true
  }
}

internal fun isIncrementalCompilationDataAvailable(context: CompilationContext): Boolean {
  return context.options.incrementalCompilation && context.compilationData.isIncrementalCompilationDataAvailable()
}

internal suspend fun doCompile(
  moduleNames: Collection<String>? = null,
  includingTestsInModules: List<String>? = null,
  availableCommitDepth: Int,
  context: CompilationContext,
  handleCompilationFailureBeforeRetry: (suspend (successMessage: String) -> String)?,
) {
  check(currentJavaVersion().isAtLeast(17)) {
    "Build script must be executed under Java 17 to compile intellij project but it's executed under Java ${currentJavaVersion()}"
  }
  check(isCompilationRequired(context.options)) {
    "Unexpected compilation request, unable to proceed"
  }
  context.compilationData.statisticsReported = false
  val runner = JpsCompilationRunner(context)
  try {
    val (status, isIncrementalCompilation) = when {
      context.options.forceRebuild -> "forced rebuild" to false
      availableCommitDepth >= 0 -> portableJpsCacheUsageStatus(availableCommitDepth) to true
      isIncrementalCompilationDataAvailable(context) -> "compile using local cache" to true
      else -> "clean build" to false
    }
    context.options.incrementalCompilation = isIncrementalCompilation
    if (isIncrementalCompilation) {
      Span.current().addEvent("status: $status")
    }
    else {
      Span.current().addEvent(
        "no compiled classes can be reused",
        Attributes.of(
          AttributeKey.stringKey("status"), status,
          AttributeKey.longKey("availableCommitDepth"), availableCommitDepth.toLong(),
        )
      )
    }

    val incrementalCompilationTimeout = context.options.incrementalCompilationTimeout
    if (isIncrementalCompilation && incrementalCompilationTimeout != null) {
      // workaround for KT-55695
      withTimeout(incrementalCompilationTimeout) {
        compile(
          jpsCompilationRunner = runner,
          context = context,
          moduleNames = moduleNames,
          includingTestsInModules = includingTestsInModules,
          canceledStatus = CanceledStatus { !isActive },
        )
      }
    }
    else {
      compile(jpsCompilationRunner = runner, context = context, moduleNames = moduleNames, includingTestsInModules = includingTestsInModules)
    }
    context.messages.buildStatus(status)
  }
  catch (e: Exception) {
    retryCompilation(
      context = context,
      runner = runner,
      moduleNames = moduleNames,
      includingTestsInModules = includingTestsInModules,
      e = e,
      handleCompilationFailureBeforeRetry = handleCompilationFailureBeforeRetry,
    )
  }
}

private suspend fun unpackCompiledClasses(classOutput: Path, context: CompilationContext) {
  spanBuilder("unpack compiled classes archive").use {
    NioFiles.deleteRecursively(classOutput)
    Decompressor.Zip(context.options.pathToCompiledClassesArchive ?: error("intellij.build.compiled.classes.archive is not set"))
      .extract(classOutput)
  }
}

private suspend fun retryCompilation(
  context: CompilationContext,
  runner: JpsCompilationRunner,
  moduleNames: Collection<String>?,
  includingTestsInModules: List<String>?,
  e: Exception,
  handleCompilationFailureBeforeRetry: (suspend (successMessage: String) -> String)?,
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
      cleanOutput(context = context, keepCompilationState = false)
      context.options.incrementalCompilation = false
    }
    handleCompilationFailureBeforeRetry != null -> {
      successMessage = handleCompilationFailureBeforeRetry(successMessage)
    }
    else -> {
      Span.current().addEvent("Incremental compilation failed. Re-trying with clean build.")
      cleanOutput(context = context, keepCompilationState = false)
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