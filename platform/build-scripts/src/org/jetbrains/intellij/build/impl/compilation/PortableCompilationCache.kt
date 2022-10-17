// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.NioFiles
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.JpsCompilationRunner
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.jps.incremental.storage.ProjectStamps
import java.nio.file.Files
import java.nio.file.Path

class PortableCompilationCache(private val context: CompilationContext) {
  companion object {
    @JvmStatic
    val IS_ENABLED = ProjectStamps.PORTABLE_CACHES && IS_CONFIGURED

    private var isAlreadyUpdated = false
  }

  init {
    require(IS_ENABLED) {
      "JPS Caches are expected to be enabled"
    }
  }

  private val git = Git(context.paths.projectHome)

  /**
   * JPS data structures allowing incremental compilation for [CompilationOutput]
   */
  private class JpsCaches(context: CompilationContext) {
    val skipDownload = bool(SKIP_DOWNLOAD_PROPERTY)
    val skipUpload = bool(SKIP_UPLOAD_PROPERTY)
    val dir: Path by lazy { context.compilationData.dataStorageRoot }

    val maybeAvailableLocally: Boolean by lazy {
      val files = dir.toFile().list()
      context.messages.info("$dir: ${files.joinToString()}")
      Files.isDirectory(dir) && files != null && files.isNotEmpty()
    }
  }

  /**
   * Server which stores [PortableCompilationCache]
   */
  private class RemoteCache(context: CompilationContext) {
    val url by lazy { require(URL_PROPERTY, "Remote Cache url", context) }
    val uploadUrl by lazy { require(UPLOAD_URL_PROPERTY, "Remote Cache upload url", context) }
  }

  private val forceDownload = bool(FORCE_DOWNLOAD_PROPERTY)
  private val forceRebuild = bool(FORCE_REBUILD_PROPERTY)
  private val remoteCache = RemoteCache(context)
  private val jpsCaches by lazy { JpsCaches(context) }
  private val remoteGitUrl by lazy {
    val result = require(GIT_REPOSITORY_URL_PROPERTY, "Repository url", context)
    context.messages.info("Git remote url $result")
    result
  }

  private val downloader by lazy {
    val availableForHeadCommit = bool(AVAILABLE_FOR_HEAD_PROPERTY)
    PortableCompilationCacheDownloader(context, git, remoteCache.url, remoteGitUrl, availableForHeadCommit, jpsCaches.skipDownload)
  }

  private val uploader by lazy {
    val s3Folder = require(AWS_SYNC_FOLDER_PROPERTY, "AWS S3 sync folder", context)
    val commitHash = require(COMMIT_HASH_PROPERTY, "Repository commit", context)
    PortableCompilationCacheUploader(context, remoteCache.uploadUrl, remoteGitUrl, commitHash, s3Folder, jpsCaches.skipUpload, forceRebuild)
  }

  /**
   * Download the latest available [PortableCompilationCache],
   * [org.jetbrains.intellij.build.CompilationTasks.resolveProjectDependencies]
   * and perform incremental compilation if necessary.
   *
   * When force rebuilding incremental compilation flag has to be set to false otherwise backward-refs won't be created.
   * During rebuild JPS checks condition [org.jetbrains.jps.backwardRefs.index.CompilerReferenceIndex.exists] || [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter.isRebuildInAllJavaModules]
   * and if incremental compilation is enabled JPS won't create [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter].
   * For more details see [org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter.initialize]
   */
  fun downloadCacheAndCompileProject() {
    synchronized(PortableCompilationCache) {
      if (isAlreadyUpdated) {
        context.messages.info("PortableCompilationCache is already updated")
        return
      }
      if (forceRebuild || forceDownload) {
        clean()
      }
      if (!forceRebuild && !isLocalCacheUsed()) {
        downloadCache()
      }
      CompilationTasks.create(context).resolveProjectDependencies()
      if (isCompilationRequired()) {
        context.options.incrementalCompilation = !forceRebuild
        context.messages.block("Compiling project") {
          compileProject(context)
        }
      }
      isAlreadyUpdated = true
      context.options.incrementalCompilation = true
    }
  }

  private fun isCompilationRequired() = forceRebuild || isLocalCacheUsed() || isRemoteCacheStale()

  private fun isLocalCacheUsed() = !forceRebuild && !forceDownload && jpsCaches.maybeAvailableLocally

  private fun isRemoteCacheStale() = !downloader.availableForHeadCommit

  /**
   * Upload local [PortableCompilationCache] to [PortableCompilationCache.RemoteCache]
   */
  fun upload() {
    if (!forceRebuild && downloader.availableForHeadCommit) {
      context.messages.info("Nothing new to upload")
    }
    else {
      uploader.upload(context.messages)
    }
    val metadataFile = context.paths.artifactDir.resolve(COMPILATION_CACHE_METADATA_JSON)
    Files.createDirectories(metadataFile.parent)
    Files.writeString(metadataFile, "This is stub file required only for TeamCity artifact dependency. " +
                                    "Compiled classes will be resolved via ${remoteCache.url}")
    context.messages.artifactBuilt("$metadataFile")
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
    // fail-fast in case of KTIJ-17296
    if (SystemInfoRt.isWindows && git.lineBreaksConfig() != "input") {
      context.messages.error("PortableCompilationCache cannot be used with CRLF line breaks, " +
                             "please execute `git config --global core.autocrlf input` before checkout " +
                             "and upvote https://youtrack.jetbrains.com/issue/KTIJ-17296")
    }
    context.compilationData.statisticsReported = false
    val jps = JpsCompilationRunner(context)
    try {
      jps.buildAll()
    }
    catch (e: Exception) {
      if (!context.options.incrementalCompilation) {
        throw e
      }
      val successMessage: String
      if (forceDownload) {
        context.messages.warning("Incremental compilation using Remote Cache failed. Re-trying without any caches.")
        clean()
        context.options.incrementalCompilation = false
        successMessage = "Compilation successful after clean build retry"
      }
      else {
        // Portable Compilation Cache is rebuilt from scratch on CI and re-published every night to avoid possible incremental compilation issues.
        // If download isn't forced then locally available cache will be used which may suffer from those issues.
        // Hence, compilation failure. Replacing local cache with remote one may help.
        context.messages.warning("Incremental compilation using locally available caches failed. Re-trying using Remote Cache.")
        downloadCache()
        successMessage = "Compilation successful after retry with fresh Remote Cache"
      }
      context.compilationData.reset()
      jps.buildAll()
      context.messages.info(successMessage)
      println("##teamcity[buildStatus status='SUCCESS' text='$successMessage']")
      context.messages.reportStatisticValue("Incremental compilation failures", "1")
    }
  }

  private fun downloadCache() {
    context.messages.block("Downloading Portable Compilation Cache") {
      downloader.download()
    }
  }
}

/**
 * [PortableCompilationCache.JpsCaches] archive upload may be skipped if only [CompilationOutput]s are required
 * without any incremental compilation (for tests execution as an example)
 */
private const val SKIP_UPLOAD_PROPERTY = "intellij.jps.remote.cache.uploadCompilationOutputsOnly"

/**
 * [PortableCompilationCache.JpsCaches] archive download may be skipped if only [CompilationOutput]s are required
 * without any incremental compilation (for tests execution as an example)
 */
private const val SKIP_DOWNLOAD_PROPERTY = "intellij.jps.remote.cache.downloadCompilationOutputsOnly"

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
