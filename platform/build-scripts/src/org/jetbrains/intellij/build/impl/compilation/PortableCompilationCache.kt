// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.cleanOutput
import org.jetbrains.intellij.build.jpsCache.getJpsCacheUrl
import org.jetbrains.intellij.build.jpsCache.isPortableCompilationCacheEnabled
import org.jetbrains.intellij.build.jpsCache.jpsCacheAuthHeader
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.net.URI
import java.nio.file.Path
import java.util.concurrent.CancellationException

private var isAlreadyUpdated = false

/**
 * Download the latest available [PortableCompilationCache],
 * [resolveProjectDependencies]
 * and perform incremental compilation if necessary.
 *
 * If rebuild is forced, an incremental compilation flag has to be set to false; otherwise backward-refs won't be created.
 * During rebuild,
 * JPS checks condition [org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex.exists] || [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter.isRebuildInAllJavaModules]
 * and if incremental compilation is enabled, JPS won't create [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter].
 * For more details see [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter.initialize]
 */
@Suppress("KDocUnresolvedReference")
internal suspend fun downloadCacheAndCompileProject(forceDownload: Boolean, gitUrl: String, context: CompilationContext) {
  val forceRebuild = context.options.forceRebuild
  spanBuilder("download JPS cache and compile")
    .setAttribute("forceRebuild", forceRebuild)
    .setAttribute("forceDownload", forceDownload)
    .use { span ->
      val cacheUrl = getJpsCacheUrl()
      if (isAlreadyUpdated) {
        span.addEvent("JPS Cache is already updated")
        return@use
      }

      check(isPortableCompilationCacheEnabled) {
        "JPS Caches are expected to be enabled"
      }

      if (forceRebuild || forceDownload) {
        cleanOutput(context = context, keepCompilationState = false)
      }

      val reportStatisticValue = context.messages::reportStatisticValue
      val portableCompilationCache = PortableCompilationCache(forceDownload = forceDownload)

      val isLocalCacheUsed = !forceRebuild && !forceDownload && isIncrementalCompilationDataAvailable(context)
      val shouldBeDownloaded = !forceRebuild && !isLocalCacheUsed
      val availableCommitDepth = if (shouldBeDownloaded) {
        portableCompilationCache.downloadCache(
          cacheUrl = cacheUrl,
          gitUrl = gitUrl,
          reportStatisticValue = reportStatisticValue,
          classOutDir = context.classesOutputDirectory,
          projectHome = context.paths.projectHome,
          context = context,
        )
      }
      else {
        throw IllegalStateException("JPS Cache should not be downloaded")
      }

      context.options.incrementalCompilation = !context.options.forceRebuild
      // compilation is executed unconditionally here even if the exact commit cache is downloaded
      // to have an additional validation step and not to ignore a local changes, for example, in TeamCity Remote Run
      if (!context.options.useCompiledClassesFromProjectOutput) {
        doCompile(
          availableCommitDepth = availableCommitDepth,
          context = context,
          handleCompilationFailureBeforeRetry = { successMessage ->
            portableCompilationCache.handleCompilationFailureBeforeRetry(
              successMessage = successMessage,
              forceDownload = portableCompilationCache.forceDownload,
              cacheUrl = cacheUrl,
              gitUrl = gitUrl,
              reportStatisticValue = reportStatisticValue,
              context = context,
            )
          },
        )
      }
      isAlreadyUpdated = true
      context.options.incrementalCompilation = true
    }
}

private class PortableCompilationCache(forceDownload: Boolean) {
  var forceDownload: Boolean = forceDownload
    private set

  /**
   * @return updated [successMessage]
   */
  suspend fun handleCompilationFailureBeforeRetry(
    cacheUrl: URI,
    successMessage: String,
    context: CompilationContext,
    forceDownload: Boolean,
    gitUrl: String,
    reportStatisticValue: (key: String, value: String) -> Unit,
  ): String {
    when {
      forceDownload -> {
        Span.current().addEvent("Incremental compilation using Remote Cache failed. Re-trying with clean build.")
        cleanOutput(context = context, keepCompilationState = false)
        context.options.incrementalCompilation = false
      }
      else -> {
        // Portable Compilation Cache is rebuilt from scratch on CI and re-published every night to avoid possible incremental compilation issues.
        // If download isn't forced, then locally available cache will be used which may suffer from those issues.
        // Hence, compilation failure. Replacing local cache with remote one may help.
        Span.current().addEvent("Incremental compilation using locally available caches failed. Re-trying using Remote Cache.")
        val availableCommitDepth = downloadCache(
          cacheUrl = cacheUrl,
          gitUrl = gitUrl,
          reportStatisticValue = reportStatisticValue,
          classOutDir = context.classesOutputDirectory,
          projectHome = context.paths.projectHome,
          context = context,
        )
        if (availableCommitDepth >= 0) {
          return portableJpsCacheUsageStatus(availableCommitDepth)
        }
      }
    }
    return successMessage
  }

  suspend fun downloadCache(
    cacheUrl: URI,
    gitUrl: String,
    reportStatisticValue: (key: String, value: String) -> Unit,
    projectHome: Path,
    classOutDir: Path,
    context: CompilationContext,
  ): Int {
    return spanBuilder("download JPS Cache").use { span ->
      try {
        downloadJpsCache(
          cacheUrl = cacheUrl,
          gitUrl = gitUrl,
          authHeader = jpsCacheAuthHeader,
          projectHome = projectHome,
          classOutDir = classOutDir,
          cacheDestination = context.compilationData.dataStorageRoot,
          reportStatisticValue = reportStatisticValue,
        )
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        span.recordException(e, Attributes.of(AttributeKey.stringKey("message"), "Failed to download JPS Cache. Re-trying without any caches."))
        context.options.forceRebuild = true
        forceDownload = false
        context.options.incrementalCompilation = false
        cleanOutput(context = context, keepCompilationState = false)
        -1
      }
    }
  }
}

internal fun portableJpsCacheUsageStatus(availableCommitDepth: Int): String {
  return when (availableCommitDepth) {
    0 -> "all classes reused from JPS remote cache"
    1 -> "1 commit compiled using JPS remote cache"
    else -> "$availableCommitDepth commits compiled using JPS remote cache"
  }
}

/**
 * Compiled bytecode of project module
 *
 * Note: cannot be used for incremental compilation without [org.jetbrains.intellij.build.JpsCompilationData.dataStorageRoot]
 */
internal class CompilationOutput(
  @JvmField val remotePath: String,
  // local path to compilation output
  @JvmField val path: Path,
)