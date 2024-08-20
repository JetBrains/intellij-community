// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import io.opentelemetry.api.trace.Span
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.cleanOutput
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.util.*
import java.util.concurrent.CancellationException

internal val IS_PORTABLE_COMPILATION_CACHE_ENABLED: Boolean
  get() = ProjectStamps.PORTABLE_CACHES && IS_CONFIGURED

private var isAlreadyUpdated = false

class PortableCompilationCache(private val context: CompilationContext) {
  private val git = Git(context.paths.projectHome)

  /**
   * Server which stores [PortableCompilationCache]
   */
  internal inner class RemoteCache {
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

    val shouldBeDownloaded: Boolean
      get() = !forceRebuild && !isLocalCacheUsed()
  }

  private var forceDownload = System.getProperty(FORCE_DOWNLOAD_PROPERTY).toBoolean()
  private val forceRebuild = context.options.forceRebuild
  private val remoteCache = RemoteCache()
  private val remoteGitUrl by lazy {
    val result = require(GIT_REPOSITORY_URL_PROPERTY, "Repository url")
    context.messages.info("Git remote url $result")
    result
  }

  private val downloader by lazy {
    PortableCompilationCacheDownloader(context = context, git = git, remoteCache = remoteCache, gitUrl = remoteGitUrl)
  }

  private val uploader by lazy {
    val s3Folder = require(AWS_SYNC_FOLDER_PROPERTY, "AWS S3 sync folder")
    val commitHash = require(COMMIT_HASH_PROPERTY, "Repository commit")
    PortableCompilationCacheUploader(
      context = context,
      remoteCache = remoteCache,
      remoteGitUrl = remoteGitUrl,
      commitHash = commitHash,
      s3Folder = s3Folder,
      forcedUpload = forceRebuild,
    )
  }

  /**
   * Download the latest available [PortableCompilationCache],
   * [org.jetbrains.intellij.build.CompilationTasks.resolveProjectDependencies]
   * and perform incremental compilation if necessary.
   *
   * If rebuild is forced, an incremental compilation flag has to be set to false; otherwise backward-refs won't be created.
   * During rebuild, JPS checks condition [org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex.exists] || [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter.isRebuildInAllJavaModules]
   * and if incremental compilation is enabled, JPS won't create [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter].
   * For more details see [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter.initialize]
   */
  internal suspend fun downloadCacheAndCompileProject() {
    if (isAlreadyUpdated) {
      Span.current().addEvent("PortableCompilationCache is already updated")
      return
    }

    check(IS_PORTABLE_COMPILATION_CACHE_ENABLED) {
      "JPS Caches are expected to be enabled"
    }
    if (forceRebuild || forceDownload) {
      clean()
    }

    val availableCommitDepth = if (remoteCache.shouldBeDownloaded) {
      downloadCache(downloader)
    }
    else {
      -1
    }
    CompilationTasks.create(context).resolveProjectDependencies()
    context.options.incrementalCompilation = !forceRebuild
    // compilation is executed unconditionally here even if the exact commit cache is downloaded
    // to have an additional validation step and not to ignore a local changes, for example, in TeamCity Remote Run
    CompiledClasses.compile(availableCommitDepth = availableCommitDepth, context = context)
    isAlreadyUpdated = true
    context.options.incrementalCompilation = true
  }

  private fun isLocalCacheUsed() = !forceRebuild && !forceDownload && CompiledClasses.isIncrementalCompilationDataAvailable(context)

  /**
   * @return updated [successMessage]
   */
  internal suspend fun handleCompilationFailureBeforeRetry(successMessage: String): String {
    check(IS_PORTABLE_COMPILATION_CACHE_ENABLED) {
      "JPS Caches are expected to be enabled"
    }
    when {
      forceDownload -> {
        Span.current().addEvent("Incremental compilation using Remote Cache failed. Re-trying with clean build.")
        clean()
        context.options.incrementalCompilation = false
      }
      else -> {
        // Portable Compilation Cache is rebuilt from scratch on CI and re-published every night to avoid possible incremental compilation issues.
        // If download isn't forced, then locally available cache will be used which may suffer from those issues.
        // Hence, compilation failure. Replacing local cache with remote one may help.
        Span.current().addEvent("Incremental compilation using locally available caches failed. Re-trying using Remote Cache.")
        val availableCommitDepth = downloadCache(downloader)
        if (availableCommitDepth >= 0) {
          return usageStatus(availableCommitDepth)
        }
      }
    }
    return successMessage
  }

  /**
   * Upload local [PortableCompilationCache] to [PortableCompilationCache.RemoteCache]
   */
  suspend fun upload() {
    uploader.upload(context.messages)
  }

  /**
   * Publish already uploaded [PortableCompilationCache] to [PortableCompilationCache.RemoteCache]
   */
  suspend fun publish() {
    uploader.updateCommitHistory()
  }

  /**
   * Publish already uploaded [PortableCompilationCache] to [PortableCompilationCache.RemoteCache] overriding existing [CommitsHistory].
   * Used in force rebuild and cleanup.
   */
  suspend fun overrideCommitHistory(forceRebuiltCommits: Set<String>) {
    uploader.updateCommitHistory(commitHistory = CommitsHistory(mapOf(remoteGitUrl to forceRebuiltCommits)), overrideRemoteHistory = true)
  }

  private suspend fun clean() {
    cleanOutput(compilationContext = context, keepCompilationState = false)
  }

  private suspend fun downloadCache(downloader: PortableCompilationCacheDownloader): Int {
    spanBuilder("downloading Portable Compilation Cache").use { span ->
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
        clean()
      }
    }
    return downloader.getAvailableCommitDepth()
  }

  internal fun usageStatus(availableCommitDepth: Int): String {
    return when (availableCommitDepth) {
      0 -> "All classes reused from JPS remote cache"
      1 -> "1 commit compiled using JPS remote cache"
      else -> "$availableCommitDepth commits compiled using JPS remote cache"
    }
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
 * If true then [PortableCompilationCache.RemoteCache] is configured to be used
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
  name: String,
  type: String,
  @JvmField val hash: String, // Some hash of compilation output, could be non-unique across different CompilationOutput's
  @JvmField val path: String, // Local path to compilation output
) {
  @JvmField val remotePath: String = "$type/$name/$hash"
}
