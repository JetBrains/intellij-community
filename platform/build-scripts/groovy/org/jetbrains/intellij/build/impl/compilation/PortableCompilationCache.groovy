// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.intellij.build.impl.CompilationContextImpl
import org.jetbrains.intellij.build.impl.JpsCompilationRunner
import org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter
import org.jetbrains.jps.incremental.storage.ProjectStamps

/**
 * Combination of {@link PortableCompilationCache.JpsCaches} and {@link org.jetbrains.intellij.build.impl.compilation.cache.CompilationOutput}s
 */
@CompileStatic
final class PortableCompilationCache {
  /**
   * JPS data structures allowing incremental compilation for {@link org.jetbrains.intellij.build.impl.compilation.cache.CompilationOutput}
   */
  @CompileStatic
  final class JpsCaches {
    /**
     * {@link JpsCaches} archive upload may be skipped if only {@link org.jetbrains.intellij.build.impl.compilation.cache.CompilationOutput}s are required
     * without any incremental compilation (for tests execution as an example)
     */
    private static final String SKIP_UPLOAD_PROPERTY = 'intellij.jps.remote.cache.uploadCompilationOutputsOnly'
    /**
     * {@link JpsCaches} archive download may be skipped if only {@link org.jetbrains.intellij.build.impl.compilation.cache.CompilationOutput}s are required
     * without any incremental compilation (for tests execution as an example)
     */
    private static final String SKIP_DOWNLOAD_PROPERTY = 'intellij.jps.remote.cache.downloadCompilationOutputsOnly'
    private final CompilationContext context
    final boolean skipDownload = bool(SKIP_DOWNLOAD_PROPERTY, false)
    final boolean skipUpload = bool(SKIP_UPLOAD_PROPERTY, false)
    @Lazy
    File dir = { context.compilationData.dataStorageRoot }()

    JpsCaches(CompilationContext context) {
      this.context = context
    }

    @Lazy
    boolean maybeAvailableLocally = {
      def files = dir.list()
      context.messages.info("$dir.absolutePath: $files")
      dir.isDirectory() && files != null && files.length > 0
    }()
  }

  /**
   * Server which stores {@link PortableCompilationCache}
   */
  @CompileStatic
  final class RemoteCache {
    /**
     * URL for read/write operations
     */
    private static final String UPLOAD_URL_PROPERTY = 'intellij.jps.remote.cache.upload.url'
    /**
     * If true then {@link RemoteCache} is configured to be used
     */
    private static final boolean IS_CONFIGURED = !StringUtil.isEmptyOrSpaces(System.getProperty(RemoteCache.URL_PROPERTY))
    /**
     * URL for read-only operations
     */
    static final String URL_PROPERTY = 'intellij.jps.remote.cache.url'

    @Lazy
    String url = { require(URL_PROPERTY, "Remote Cache url") }()

    @Lazy
    String uploadUrl = { require(UPLOAD_URL_PROPERTY, "Remote Cache upload url") }()
  }

  private final CompilationContext context
  private final Git git
  /**
   * IntelliJ repository git remote url
   */
  private static final String GIT_REPOSITORY_URL_PROPERTY = 'intellij.remote.url'
  /**
   * If true then {@link PortableCompilationCache} for head commit is expected to exist and search in
   * {@link org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory#JSON_FILE} is skipped.
   * Required for temporary branch caches which are uploaded but not published in
   * {@link org.jetbrains.intellij.build.impl.compilation.cache.CommitsHistory#JSON_FILE}.
   */
  private static final String AVAILABLE_FOR_HEAD_PROPERTY = 'intellij.jps.cache.availableForHeadCommit'
  /**
   * Download {@link PortableCompilationCache} even if there are caches available locally
   */
  private static final String FORCE_DOWNLOAD_PROPERTY = 'intellij.jps.cache.download.force'
  /**
   * If true then {@link PortableCompilationCache} will be rebuilt from scratch
   */
  private static final String FORCE_REBUILD_PROPERTY = 'intellij.jps.cache.rebuild.force'
  /**
   * Folder to store {@link PortableCompilationCache} for later upload to AWS S3 bucket.
   * Upload performed in a separate process on CI.
   */
  private static final String AWS_SYNC_FOLDER_PROPERTY = 'jps.caches.aws.sync.folder'
  /**
   * Commit hash for which {@link PortableCompilationCache} is to be built/downloaded
   */
  private static final String COMMIT_HASH_PROPERTY = 'build.vcs.number'
  static final boolean CAN_BE_USED = ProjectStamps.PORTABLE_CACHES && PortableCompilationCache.RemoteCache.IS_CONFIGURED
  private final boolean forceDownload = bool(FORCE_DOWNLOAD_PROPERTY, false)
  private final boolean forceRebuild = bool(FORCE_REBUILD_PROPERTY, false)
  private final RemoteCache remoteCache = new RemoteCache()
  @Lazy
  private JpsCaches jpsCaches = { new JpsCaches(context) }()
  final boolean canBeUsed = CAN_BE_USED

  @Lazy
  private String remoteGitUrl = {
    require(GIT_REPOSITORY_URL_PROPERTY, "Repository url").tap {
      context.messages.info("Git remote url $it")
    }
  }()

  @Lazy
  private PortableCompilationCacheDownloader downloader = {
    def availableForHeadCommit = bool(AVAILABLE_FOR_HEAD_PROPERTY, false)
    new PortableCompilationCacheDownloader(context, git, remoteCache.url, remoteGitUrl,
                                           availableForHeadCommit, jpsCaches.skipDownload)
  }()

  @Lazy
  private PortableCompilationCacheUploader uploader = {
    def syncFolder = require(AWS_SYNC_FOLDER_PROPERTY, "AWS sync folder")
    def commitHash = require(COMMIT_HASH_PROPERTY, "Repository commit")
    context.messages.buildStatus(commitHash)
    new PortableCompilationCacheUploader(context, remoteCache.uploadUrl, remoteGitUrl, commitHash,
                                         syncFolder, jpsCaches.skipUpload, forceRebuild)
  }()

  private static boolean isAlreadyUpdated

  PortableCompilationCache(CompilationContext context) {
    this.context = context
    this.git = new Git(context.paths.projectHome.trim())
  }

  /**
   * Download latest available {@link PortableCompilationCache} and perform incremental compilation if necessary
   *
   * When force rebuilding incremental compilation flag has to be set to false otherwise backward-refs won't be created.
   * During rebuild JPS checks {@code CompilerReferenceIndex.exists(buildDir) || isRebuild} and if
   * incremental compilation is enabled JPS won't create {@link JavaBackwardReferenceIndexWriter}.
   * For more details see {@link JavaBackwardReferenceIndexWriter#initialize}
   */
  def downloadCacheAndCompileProject() {
    //noinspection GroovySynchronizationOnNonFinalField
    synchronized (PortableCompilationCache) {
      if (isAlreadyUpdated) {
        context.messages.info("${getClass().simpleName} is already updated")
        return
      }
      if (forceRebuild) {
        clean()
      }
      else if (!isLocalCacheUsed()) {
        downloadCache()
      }
      CompilationTasks.create(context).resolveProjectDependencies()
      if (isCompilationRequired()) {
        context.options.incrementalCompilation = !forceRebuild
        compileProject()
      }
      isAlreadyUpdated = true
      context.options.incrementalCompilation = true
      context.options.useCompiledClassesFromProjectOutput = false
    }
  }

  boolean isCompilationRequired() {
    return forceRebuild || isLocalCacheUsed() || isRemoteCacheStale()
  }

  private boolean isLocalCacheUsed() {
    return !forceRebuild && !forceDownload && jpsCaches.maybeAvailableLocally
  }

  private boolean isRemoteCacheStale() {
    return !downloader.availableForHeadCommit || downloader.anyLocalChanges
  }

  /**
   * Upload local {@link PortableCompilationCache} to {@link RemoteCache}
   */
  def upload() {
    if (!forceRebuild && downloader.availableForHeadCommit) {
      context.messages.info('Nothing new to upload')
    }
    else {
      uploader.upload()
    }
  }

  /**
   * Publish already uploaded {@link PortableCompilationCache} to {@link RemoteCache}
   */
  void publish() {
    uploader.updateCommitHistory()
  }

  File buildJpsCacheZip() {
    uploader.buildJpsCacheZip()
  }

  /**
   * Publish already uploaded {@link PortableCompilationCache} to {@link RemoteCache} overriding existing {@link CommitsHistory}.
   * Used in force rebuild and cleanup.
   */
  void overrideCommitHistory(Set<String> forceRebuiltCommits) {
    def newCommitHistory = new CommitsHistory([(remoteGitUrl): forceRebuiltCommits])
    uploader.updateCommitHistory(newCommitHistory, true)
  }

  private def clean() {
    [jpsCaches.dir, context.projectOutputDirectory].each {
      context.messages.info("Cleaning $it")
      FileUtil.delete(it)
    }
  }

  private def compileProject() {
    // fail-fast in case of KTIJ-17296
    if (SystemInfo.isWindows && git.lineBreaksConfig() != "input") {
      context.messages.error("${getClass().simpleName} cannot be used with CRLF line breaks, " +
                             "please execute `git config --global core.autocrlf input` before checkout " +
                             "and upvote https://youtrack.jetbrains.com/issue/KTIJ-17296")
    }
    def jps = new JpsCompilationRunner(context)
    try {
      jps.buildAll()
    }
    catch (Exception e) {
      if (context.options.incrementalCompilation && !forceDownload) {
        // Portable Compilation Cache is rebuilt from scratch on CI and re-published every night to avoid possible incremental compilation issues.
        // If download isn't forced then locally available cache will be used which may suffer from those issues.
        // Hence compilation failure. Replacing local cache with remote one may help.
        context.messages.warning('Incremental compilation using locally available caches failed. ' +
                                 'Re-trying using Remote Cache.')
        downloadCache()
        jps.buildAll()
      }
      else {
        throw e
      }
    }
  }

  private def downloadCache() {
    try {
      downloader.download()
    }
    finally {
      downloader.close()
    }
  }

  private String require(String systemProperty, String description) {
    def value = System.getProperty(systemProperty)
    if (StringUtil.isEmptyOrSpaces(value)) {
      context.messages.error("$description is not defined. Please set '$systemProperty' system property.")
    }
    return value
  }

  private static boolean bool(String systemProperty, boolean defaultValue) {
    System.getProperty(systemProperty, "$defaultValue").toBoolean()
  }
}