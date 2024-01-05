// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.cleanOutput
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.util.*

class PortableCompilationCache(private val context: CompilationContext) {
  companion object {
    @JvmStatic
    val IS_ENABLED = ProjectStamps.PORTABLE_CACHES && IS_CONFIGURED

    private var isAlreadyUpdated = false
  }

  private val git = Git(context.paths.projectHome)

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

    val shouldBeDownloaded: Boolean get() = !forceRebuild && !isLocalCacheUsed()
    val shouldBeUploaded by lazy { bool(UPLOAD_PROPERTY) }
    val shouldBeSyncedToS3 by lazy { !bool("jps.caches.aws.sync.skip") }
  }

  private var forceDownload = bool(FORCE_DOWNLOAD_PROPERTY)
  private val forceRebuild = context.options.forceRebuild
  private val remoteCache = RemoteCache(context)
  private val remoteGitUrl by lazy {
    val result = require(GIT_REPOSITORY_URL_PROPERTY, "Repository url", context)
    context.messages.info("Git remote url $result")
    result
  }

  private val downloader by lazy {
    PortableCompilationCacheDownloader(context, git, remoteCache, remoteGitUrl)
  }

  private val uploader by lazy {
    val s3Folder = require(AWS_SYNC_FOLDER_PROPERTY, "AWS S3 sync folder", context)
    val commitHash = require(COMMIT_HASH_PROPERTY, "Repository commit", context)
    PortableCompilationCacheUploader(context, remoteCache, remoteGitUrl, commitHash, s3Folder, forceRebuild)
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
  internal fun downloadCacheAndCompileProject() {
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
      context.options.incrementalCompilation = !forceRebuild
      // compilation is executed unconditionally here even if exact commit cache is downloaded
      // to have an additional validation step and not to ignore a local changes, for example in TeamCity Remote Run
      CompiledClasses.compile(context, isPortableCacheDownloaded = remoteCache.shouldBeDownloaded && downloader.availableCommitDepth >= 0)
      isAlreadyUpdated = true
      context.options.incrementalCompilation = true
    }
  }

  private fun isLocalCacheUsed() = !forceRebuild && !forceDownload &&
                                   CompiledClasses.isIncrementalCompilationDataAvailable(context)

  /**
   * @return updated [successMessage]
   */
  internal fun handleCompilationFailureBeforeRetry(successMessage: String): String {
    check(IS_ENABLED) {
      "JPS Caches are expected to be enabled"
    }
    when {
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
          return usageStatus()
        }
      }
    }
    return successMessage
  }

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
    context.cleanOutput(keepCompilationState = false)
  }

  private fun downloadCache() {
    context.messages.block("Downloading Portable Compilation Cache") {
      try {
        downloader.download()
      }
      catch (e: Exception) {
        e.printStackTrace()
        context.messages.warning("Failed to download Compilation Cache. Re-trying without any caches.")
        context.options.forceRebuild = true
        forceDownload = false
        context.options.incrementalCompilation = false
        clean()
      }
    }
  }

  internal fun usageStatus(): String {
    return when (downloader.availableCommitDepth) {
      0 -> "All classes reused from JPS remote cache"
      1 -> "1 commit compiled using JPS remote cache"
      else -> "${downloader.availableCommitDepth} commits compiled using JPS remote cache"
    }
  }
}

/**
 * If false then nothing will be uploaded
 */
private const val UPLOAD_PROPERTY = "intellij.jps.remote.cache.upload"

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
  val remotePath = "$type/$name/$hash"
}
