// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.BuildPaths.Companion.ULTIMATE_HOME
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.impl.cleanOutput
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.intellij.build.impl.createCompilationContext
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.block
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.nio.file.Path
import java.util.*
import java.util.concurrent.CancellationException

internal val IS_PORTABLE_COMPILATION_CACHE_ENABLED: Boolean
  get() = ProjectStamps.PORTABLE_CACHES && IS_CONFIGURED

private var isAlreadyUpdated = false

internal object TestJpsCompilationCacheDownload {
  @JvmStatic
  fun main(args: Array<String>) = runBlocking(Dispatchers.Default) {
    System.setProperty("jps.cache.test", "true")
    System.setProperty("org.jetbrains.jps.portable.caches", "true")
    if (System.getProperty(URL_PROPERTY) == null) {
      System.setProperty(URL_PROPERTY, "https://127.0.0.1:1900")
    }

    val projectHome = ULTIMATE_HOME
    val outputDir = projectHome.resolve("out/compilation")
    val context = createCompilationContext(
      projectHome = projectHome,
      defaultOutputRoot = outputDir,
      options = BuildOptions(
        incrementalCompilation = true,
        useCompiledClassesFromProjectOutput = true,
      ),
    )
    downloadCacheAndCompileProject(forceDownload = false, context = context)
  }
}

internal suspend fun downloadJpsCacheAndCompileProject(context: CompilationContext) {
  downloadCacheAndCompileProject(forceDownload = System.getProperty(FORCE_DOWNLOAD_PROPERTY).toBoolean(), context = context)
}

/**
 * Upload local [PortableCompilationCache] to [PortableJpsCacheRemoteCacheConfig]
 */
suspend fun uploadPortableCompilationCache(context: CompilationContext) {
  createPortableCompilationCacheUploader(context).upload(context.messages)
}

/**
 * Publish already uploaded [PortableCompilationCache] to [PortableJpsCacheRemoteCacheConfig]
 */
suspend fun publishPortableCompilationCache(context: CompilationContext) {
  createPortableCompilationCacheUploader(context).updateCommitHistory()
}

/**
 * Publish already uploaded [PortableCompilationCache] to [PortableJpsCacheRemoteCacheConfig] overriding existing [CommitsHistory].
 * Used in force rebuild and cleanup.
 */
suspend fun publishUploadedJpsCacheWithCommitHistoryOverride(forceRebuiltCommits: Set<String>, context: CompilationContext) {
  createPortableCompilationCacheUploader(context).updateCommitHistory(overrideCommits = forceRebuiltCommits)
}

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
private suspend fun downloadCacheAndCompileProject(forceDownload: Boolean, context: CompilationContext) {
  val forceRebuild = context.options.forceRebuild
  spanBuilder("download JPS cache and compile")
    .setAttribute("forceRebuild", forceRebuild)
    .setAttribute("forceDownload", forceDownload)
    .block { span ->
      if (isAlreadyUpdated) {
        span.addEvent("PortableCompilationCache is already updated")
        return@block
      }

      check(IS_PORTABLE_COMPILATION_CACHE_ENABLED) {
        "JPS Caches are expected to be enabled"
      }

      if (forceRebuild || forceDownload) {
        cleanOutput(context = context, keepCompilationState = false)
      }

      val downloader = PortableCompilationCacheDownloader(
        context = context,
        git = Git(context.paths.projectHome),
        remoteCache = PortableJpsCacheRemoteCacheConfig(),
        gitUrl = computeRemoteGitUrl(),
      )

      val portableCompilationCache = PortableCompilationCache(forceDownload = forceDownload)
      val availableCommitDepth = if (!forceRebuild && (forceDownload || !isIncrementalCompilationDataAvailable(context))) {
        portableCompilationCache.downloadCache(downloader, context)
      }
      else {
        -1
      }

      context.options.incrementalCompilation = !forceRebuild
      // compilation is executed unconditionally here even if the exact commit cache is downloaded
      // to have an additional validation step and not to ignore a local changes, for example, in TeamCity Remote Run
      doCompile(
        availableCommitDepth = availableCommitDepth,
        context = context,
        handleCompilationFailureBeforeRetry = { successMessage ->
          portableCompilationCache.handleCompilationFailureBeforeRetry(
            successMessage = successMessage,
            portableCompilationCacheDownloader = downloader,
            forceDownload = portableCompilationCache.forceDownload,
            context = context,
          )
        },
      )
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
    successMessage: String,
    portableCompilationCacheDownloader: PortableCompilationCacheDownloader,
    context: CompilationContext,
    forceDownload: Boolean,
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
        val availableCommitDepth = downloadCache(portableCompilationCacheDownloader, context)
        if (availableCommitDepth >= 0) {
          return portableJpsCacheUsageStatus(availableCommitDepth)
        }
      }
    }
    return successMessage
  }

  suspend fun downloadCache(downloader: PortableCompilationCacheDownloader, context: CompilationContext): Int {
    return spanBuilder("downloading Portable Compilation Cache").use { span ->
      try {
        downloader.download()
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        e.printStackTrace()
        span.addEvent("Failed to download Compilation Cache. Re-trying without any caches.")
        span.recordException(e)
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
 * URL for read/write operations
 */
private const val UPLOAD_URL_PROPERTY = "intellij.jps.remote.cache.upload.url"

/**
 * URL for read-only operations
 */
private const val URL_PROPERTY = "intellij.jps.remote.cache.url"

/**
 * If true then [PortableJpsCacheRemoteCacheConfig] is configured to be used
 */
private val IS_CONFIGURED = !System.getProperty(URL_PROPERTY).isNullOrBlank()

/**
 * IntelliJ repository git remote url
 */
private const val GIT_REPOSITORY_URL_PROPERTY = "intellij.remote.url"

/**
 * Download [PortableCompilationCache] even if there are caches available locally
 */
private const val FORCE_DOWNLOAD_PROPERTY = "intellij.jps.cache.download.force"

/**
 * Folder to store [PortableCompilationCache] for later upload to AWS S3 bucket.
 * Upload performed in a separate process on CI.
 */
private const val AWS_SYNC_FOLDER_PROPERTY = "jps.caches.aws.sync.folder"

/**
 * Commit hash for which [PortableCompilationCache] is to be built/downloaded
 */
private const val COMMIT_HASH_PROPERTY = "build.vcs.number"

private fun require(systemProperty: String, description: String): String {
  val value = System.getProperty(systemProperty)
  require(!value.isNullOrBlank()) {
    "$description is not defined. Please set '$systemProperty' system property."
  }
  return value
}

/**
 * Compiled bytecode of project module
 *
 * Note: cannot be used for incremental compilation without [org.jetbrains.intellij.build.impl.JpsCompilationData.dataStorageRoot]
 */
internal class CompilationOutput(
  @JvmField val remotePath: String,
  // local path to compilation output
  @JvmField val path: Path,
)

/**
 * Server which stores [PortableCompilationCache]
 */
internal class PortableJpsCacheRemoteCacheConfig {
  val url by lazy { require(URL_PROPERTY, "Remote Cache url") }
  val uploadUrl by lazy { require(UPLOAD_URL_PROPERTY, "Remote Cache upload url") }
  val authHeader by lazy {
    val username = System.getProperty("jps.auth.spaceUsername")
    val password = System.getProperty("jps.auth.spacePassword")
    when {
      password == null -> ""
      username == null -> "Bearer $password"
      else -> "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
    }
  }
}

internal fun createPortableCompilationCacheUploader(context: CompilationContext): PortableCompilationCacheUploader {
  return PortableCompilationCacheUploader(
    context = context,
    remoteCache = PortableJpsCacheRemoteCacheConfig(),
    remoteGitUrl = computeRemoteGitUrl(),
    commitHash = require(COMMIT_HASH_PROPERTY, "Repository commit"),
    s3Folder = Path.of(require(AWS_SYNC_FOLDER_PROPERTY, "AWS S3 sync folder")),
    forcedUpload = context.options.forceRebuild,
  )
}

private fun computeRemoteGitUrl(): String {
  val remoteGitUrl = require(GIT_REPOSITORY_URL_PROPERTY, "Repository url")
  Span.current().addEvent("Git remote url $remoteGitUrl")
  return remoteGitUrl
}