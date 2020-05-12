// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.jps.backwardRefs.JavaBackwardReferenceIndexWriter
import org.jetbrains.jps.incremental.storage.ProjectStamps

@CompileStatic
class PortableCompilationCache {
  private final CompilationContext context
  private static final String REMOTE_CACHE_URL_PROPERTY = 'intellij.jps.remote.cache.url'
  private static final String GIT_REPOSITORY_URL_PROPERTY = 'intellij.remote.url'
  private static final String AVAILABLE_FOR_HEAD_PROPERTY = 'intellij.jps.cache.availableForHeadCommit'
  private static final String FORCE_DOWNLOAD_PROPERTY = 'intellij.jps.cache.download.force'
  static final List<String> PROPERTIES = [
    REMOTE_CACHE_URL_PROPERTY, GIT_REPOSITORY_URL_PROPERTY,
    AVAILABLE_FOR_HEAD_PROPERTY, FORCE_DOWNLOAD_PROPERTY,
    JavaBackwardReferenceIndexWriter.PROP_KEY,
    ProjectStamps.PORTABLE_CACHES_PROPERTY
  ]
  @Lazy
  private String remoteGitUrl = { require(GIT_REPOSITORY_URL_PROPERTY, "Repository url") }()
  @Lazy
  private String remoteCacheUrl = { require(REMOTE_CACHE_URL_PROPERTY, "JPS remote cache url") }()
  /**
   * If true then current execution is expected to perform only warm up and upload of new commits caches, nothing else like tests execution
   */
  private boolean uploadOnly = System.getProperty('intellij.jps.cache.uploadOnly')?.toBoolean() ?: false
  @Lazy
  private CompilationOutputsDownloader downloader = {
    def availableForHeadCommit = System.getProperty(AVAILABLE_FOR_HEAD_PROPERTY)?.toBoolean() ?: false
    new CompilationOutputsDownloader(context, remoteCacheUrl, remoteGitUrl, availableForHeadCommit)
  }()
  private File cacheDir = context.compilationData.dataStorageRoot
  private boolean forceRebuild = System.getProperty('intellij.jps.cache.rebuild.force')?.toBoolean() ?: false
  boolean canBeUsed = ProjectStamps.PORTABLE_CACHES && System.getProperty(REMOTE_CACHE_URL_PROPERTY)?.with {
    !StringUtil.isEmptyOrSpaces(it)
  } == true

  PortableCompilationCache(CompilationContext context) {
    this.context = context
  }

  private String require(String systemProperty, String description) {
    def value = System.getProperty(systemProperty)
    if (StringUtil.isEmptyOrSpaces(value)) {
      context.messages.error("$description is not defined. Please set '$systemProperty' system property.")
    }
    return value
  }

  private def clearJpsOutputs() {
    [cacheDir, new File(context.paths.buildOutputRoot, 'classes')].each {
      context.messages.info("Cleaning $it")
      FileUtil.delete(it)
    }
  }

  private def compileProject() {
    if (forceRebuild || !downloader.availableForHeadCommit) {
      // When force rebuilding incrementalCompilation has to be set to false otherwise backward-refs won't be created.
      // During rebuild JPS checks {@code CompilerReferenceIndex.exists(buildDir) || isRebuild} and if
      // incremental compilation enabled JPS won't create {@link JavaBackwardReferenceIndexWriter}.
      // For more details see {@link JavaBackwardReferenceIndexWriter#initialize}
      context.options.incrementalCompilation = !forceRebuild
      CompilationTasks.create(context).resolveProjectDependenciesAndCompileAll()
    }
    context.options.incrementalCompilation = false
    context.options.useCompiledClassesFromProjectOutput = true
  }

  /**
   * Download latest available compilation cache from remote cache and perform compilation if necessary
   */
  def warmUp() {
    def forceDownload = System.getProperty(FORCE_DOWNLOAD_PROPERTY)?.toBoolean() ?: false
    if (forceRebuild) {
      clearJpsOutputs()
    }
    else if (uploadOnly && downloader.availableForHeadCommit) {
      context.messages.info('Downloading is skipped because caches are ' +
                            'available for the head commit so nothing new would be uploaded ' +
                            '(current execution is expected to perform only upload of new commits caches)')
    }
    else if (forceDownload || !cacheDir.isDirectory() || !cacheDir.list()) {
      try {
        downloader.downloadCachesAndOutput()
      }
      finally {
        downloader.close()
      }
    }
    compileProject()
  }

  /**
   * Upload local compilation cache to remote cache
   */
  def upload(Boolean publishTeamCityArtifacts) {
    if (!forceRebuild && downloader.availableForHeadCommit) {
      context.messages.info('Nothing new to upload')
    }
    else {
      def remoteCacheUrl = require('intellij.jps.remote.cache.upload.url', "JPS remote cache upload url")
      def syncFolder = require("jps.caches.aws.sync.folder", "AWS sync folder")
      def commitHash = require("build.vcs.number", "Repository commit")
      context.messages.buildStatus(commitHash)
      def updateCommitHistory = System.getProperty('intellij.jps.remote.cache.updateHistory')?.toBoolean() ?: true
      context.messages.info("Git remote url $remoteGitUrl")
      Map<String, String> remotePerCommitHash = [:]
      remotePerCommitHash[remoteGitUrl] = commitHash
      new CompilationOutputsUploader(
        context, remoteCacheUrl, remotePerCommitHash, syncFolder, updateCommitHistory
      ).upload(publishTeamCityArtifacts)
      context.messages.block("AWS sync folder $syncFolder") {
        def root = new File(syncFolder)
        root.eachFileRecurse {
          if (!it.isDirectory()) {
            context.messages.info(root.toPath().relativize(it.toPath()).toString())
          }
        }
      }
    }
  }
}
