// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.BuildOptions
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.JpsCompilationRunner
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.jps.api.CanceledStatus
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.nio.file.Path
import java.util.*
import kotlin.time.Duration.Companion.minutes

class PortableCompilationCache(private val context: CompilationContext) {
  companion object {
    @JvmStatic
    val IS_ENABLED = ProjectStamps.PORTABLE_CACHES && IS_CONFIGURED

    private var isAlreadyUpdated = false
  }

  private val git = Git(context.paths.projectHome)

  /**
   * JPS data structures allowing incremental compilation for [CompilationOutput]
   */
  private class JpsCaches(private val context: CompilationContext) {
    val downloadCompilationOutputsOnly = bool(DOWNLOAD_COMPILATION_ONLY_PROPERTY)
    val uploadCompilationOutputsOnly = bool(UPLOAD_COMPILATION_ONLY_PROPERTY)
    val dir: Path get() = context.compilationData.dataStorageRoot

    val isIncrementalCompilationDataAvailable: Boolean by lazy {
      context.options.incrementalCompilation &&
      context.compilationData.isIncrementalCompilationDataAvailable()
    }
  }

  /**
   * Server which stores [PortableCompilationCache]
   */
  internal inner class RemoteCache(context: CompilationContext) {
    val url by lazy { require(URL_PROPERTY, "Remote Cache url", context) }
    val uploadUrl by lazy { require(UPLOAD_URL_PROPERTY, "Remote Cache upload url", context) }
    val authHeader by lazy {
      val username = System.getProperty("jps.auth.spaceUsername")
      val password = System.getProperty("jps.auth.spacePassword")
      when {
        password == null -> ""
        username == null -> "Bearer $password"
        else -> "Basic " + Base64.getEncoder().encodeToString("$username:$password".toByteArray())
      }
    }

    val isStale: Boolean get() = !downloader.availableForHeadCommit
    val shouldBeDownloaded: Boolean get() = !forceRebuild && !isLocalCacheUsed()
    val shouldBeUploaded by lazy { bool(UPLOAD_PROPERTY) }
    val shouldBeSyncedToS3 by lazy { !bool("jps.caches.aws.sync.skip") }
  }

  private var forceDownload = bool(FORCE_DOWNLOAD_PROPERTY)
  private var forceRebuild = bool(FORCE_REBUILD_PROPERTY)
  private val remoteCache = RemoteCache(context)
  private val jpsCaches by lazy { JpsCaches(context) }
  private val remoteGitUrl by lazy {
    val result = require(GIT_REPOSITORY_URL_PROPERTY, "Repository url", context)
    context.messages.info("Git remote url $result")
    result
  }

  private val downloader by lazy {
    val availableForHeadCommit = bool(AVAILABLE_FOR_HEAD_PROPERTY)
    PortableCompilationCacheDownloader(context, git, remoteCache, remoteGitUrl, availableForHeadCommit, jpsCaches.downloadCompilationOutputsOnly)
  }

  private val uploader by lazy {
    val s3Folder = require(AWS_SYNC_FOLDER_PROPERTY, "AWS S3 sync folder", context)
    val commitHash = require(COMMIT_HASH_PROPERTY, "Repository commit", context)
    PortableCompilationCacheUploader(context, remoteCache, remoteGitUrl, commitHash, s3Folder, jpsCaches.uploadCompilationOutputsOnly, forceRebuild)
  }

  /**
   * Download the latest available [PortableCompilationCache],
   * [org.jetbrains.intellij.build.CompilationTasks.resolveProjectDependencies]
   * and perform incremental compilation if necessary.
   *
   * If rebuild is forced, incremental compilation flag has to be set to false, otherwise backward-refs won't be created.
   * During rebuild, JPS checks condition [org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex.exists] || [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter.isRebuildInAllJavaModules]
   * and if incremental compilation is enabled, JPS won't create [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter].
   * For more details see [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter.initialize]
   */
  fun downloadCacheAndCompileProject() {
    synchronized(PortableCompilationCache) {
      if (isAlreadyUpdated) {
        context.messages.info("PortableCompilationCache is already updated")
        return
      }
      check(IS_ENABLED) {
        "JPS Caches are expected to be enabled"
      }
      if (forceRebuild || forceDownload) {
        clean()
      }
      if (remoteCache.shouldBeDownloaded) {
        downloadCache()
      }
      CompilationTasks.create(context).resolveProjectDependencies()
      if (isCompilationRequired()) {
        context.options.incrementalCompilation = !forceRebuild
        compileProject(context)
      }
      isAlreadyUpdated = true
      context.options.incrementalCompilation = true
    }
  }

  private fun isCompilationRequired() = forceRebuild || isLocalCacheUsed() || remoteCache.isStale

  private fun isLocalCacheUsed() = !forceRebuild && !forceDownload && jpsCaches.isIncrementalCompilationDataAvailable

  /**
   * Upload local [PortableCompilationCache] to [PortableCompilationCache.RemoteCache]
   */
  fun upload() {
    if (remoteCache.shouldBeUploaded) {
      uploader.upload(context.messages)
    }
  }

  /**
   * Publish already uploaded [PortableCompilationCache] to [PortableCompilationCache.RemoteCache]
   */
  fun publish() {
    uploader.updateCommitHistory()
  }

  /**
   * Publish already uploaded [PortableCompilationCache] to [PortableCompilationCache.RemoteCache] overriding existing [CommitsHistory].
   * Used in force rebuild and cleanup.
   */
  fun overrideCommitHistory(forceRebuiltCommits: Set<String>) {
    uploader.updateCommitHistory(CommitsHistory(mapOf(remoteGitUrl to forceRebuiltCommits)), true)
  }

  private fun clean() {
    for (it in listOf(jpsCaches.dir, context.classesOutputDirectory)) {
      context.messages.info("Cleaning $it")
      NioFiles.deleteRecursively(it)
    }
  }

  private fun compileProject(context: CompilationContext) {
    check(CompiledClasses.isCompilationRequired(context.options)) {
      "Unexpected compilation request, unable to proceed"
    }
    // fail-fast in case of KTIJ-17296
    if (SystemInfoRt.isWindows && git.lineBreaksConfig() != "input") {
      context.messages.error("PortableCompilationCache cannot be used with CRLF line breaks, " +
                             "please execute `git config --global core.autocrlf input` before checkout " +
                             "and upvote https://youtrack.jetbrains.com/issue/KTIJ-17296")
    }
    context.compilationData.statisticsReported = false
    val jps = JpsCompilationRunner(context)
    try {
      val (status, isIncrementalCompilation) = when {
        forceRebuild -> "Forced rebuild" to false
        remoteCache.shouldBeDownloaded && downloader.availableCommitDepth > 0 -> remoteCacheUsage() to true
        jpsCaches.isIncrementalCompilationDataAvailable -> "Compiled using local cache" to true
        else -> "Clean build" to false
      }
      context.messages.block(status) {
        if (isIncrementalCompilation) runBlocking {
          // workaround for KT-55695
          withTimeout(context.options.incrementalCompilationTimeout.minutes) {
            launch {
              jps.buildAll(CanceledStatus { !isActive })
            }
          }
        }
        else jps.buildAll()
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
      var successMessage = "Clean build retry"
      when {
        e is TimeoutCancellationException -> {
          context.messages.reportBuildProblem("Incremental compilation timed out. Re-trying with clean build.")
          successMessage = "$successMessage after timeout"
          clean()
          context.options.incrementalCompilation = false
        }
        forceDownload -> {
          context.messages.warning("Incremental compilation using Remote Cache failed. Re-trying with clean build.")
          clean()
          context.options.incrementalCompilation = false
        }
        else -> {
          // Portable Compilation Cache is rebuilt from scratch on CI and re-published every night to avoid possible incremental compilation issues.
          // If download isn't forced then locally available cache will be used which may suffer from those issues.
          // Hence, compilation failure. Replacing local cache with remote one may help.
          context.messages.warning("Incremental compilation using locally available caches failed. Re-trying using Remote Cache.")
          downloadCache()
          if (downloader.availableCommitDepth >= 0) {
            successMessage = remoteCacheUsage()
          }
        }
      }
      context.compilationData.reset()
      context.messages.block(successMessage, jps::buildAll)
      context.messages.changeBuildStatusToSuccess(successMessage)
      context.messages.reportStatisticValue("Incremental compilation failures", "1")
    }
  }

  private fun downloadCache() {
    context.messages.block("Downloading Portable Compilation Cache") {
      try {
        downloader.download()
      }
      catch (e: Exception) {
        if (downloader.availableForHeadCommit && jpsCaches.downloadCompilationOutputsOnly) {
          throw e
        }
        e.printStackTrace()
        context.messages.warning("Failed to download Compilation Cache. Re-trying without any caches.")
        forceRebuild = true
        forceDownload = false
        context.options.incrementalCompilation = false
        clean()
      }
    }
  }

  private fun remoteCacheUsage(): String {
    return when (downloader.availableCommitDepth) {
      0 -> "All classes reused from Jps remote cache"
      1 -> "1 commit compiled using Jps remote cache"
      else -> "${downloader.availableCommitDepth} commits compiled using Jps remote cache"
    }
  }
}

/**
 * If false then nothing will be uploaded
 */
private const val UPLOAD_PROPERTY = "intellij.jps.remote.cache.upload"

/**
 * [PortableCompilationCache.JpsCaches] archive upload may be skipped if only [CompilationOutput]s are required
 * without any incremental compilation (for tests execution as an example)
 */
private const val UPLOAD_COMPILATION_ONLY_PROPERTY = "intellij.jps.remote.cache.uploadCompilationOutputsOnly"

/**
 * [PortableCompilationCache.JpsCaches] archive download may be skipped if only [CompilationOutput]s are required
 * without any incremental compilation (for tests execution as an example)
 */
private const val DOWNLOAD_COMPILATION_ONLY_PROPERTY = "intellij.jps.remote.cache.downloadCompilationOutputsOnly"

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
 * If true then [PortableCompilationCache] for head commit is expected to exist and search in [CommitsHistory.JSON_FILE] is skipped.
 * Required for temporary branch caches which are uploaded but not published in [CommitsHistory.JSON_FILE].
 */
private const val AVAILABLE_FOR_HEAD_PROPERTY = "intellij.jps.cache.availableForHeadCommit"

/**
 * Download [PortableCompilationCache] even if there are caches available locally
 */
private const val FORCE_DOWNLOAD_PROPERTY = "intellij.jps.cache.download.force"

/**
 * If true then [PortableCompilationCache] will be rebuilt from scratch
 */
private const val FORCE_REBUILD_PROPERTY = "intellij.jps.cache.rebuild.force"
/**
 * Folder to store [PortableCompilationCache] for later upload to AWS S3 bucket.
 * Upload performed in a separate process on CI.
 */
private const val AWS_SYNC_FOLDER_PROPERTY = "jps.caches.aws.sync.folder"

/**
 * Commit hash for which [PortableCompilationCache] is to be built/downloaded
 */
private const val COMMIT_HASH_PROPERTY = "build.vcs.number"

private fun require(systemProperty: String, description: String, context: CompilationContext): String {
  val value = System.getProperty(systemProperty)
  if (value.isNullOrBlank()) {
    context.messages.error("$description is not defined. Please set '$systemProperty' system property.")
  }
  return value
}

private fun bool(systemProperty: String): Boolean {
  return System.getProperty(systemProperty).toBoolean()
}

/**
 * Compiled bytecode of project module, cannot be used for incremental compilation without [PortableCompilationCache.JpsCaches]
 */
internal class CompilationOutput(
  name: String,
  type: String,
  @JvmField val hash: String, // Some hash of compilation output, could be non-unique across different CompilationOutput's
  @JvmField val path: String, // Local path to compilation output
) {
  val remotePath = "$type/$name/$hash"
}
