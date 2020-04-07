// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks
import org.jetbrains.jps.incremental.storage.ProjectStamps

@CompileStatic
class PortableCompilationCache {
  private final CompilationContext context
  private static final String REMOTE_CACHE_URL_PROPERTY = 'intellij.jps.remote.cache.url'
  @Lazy
  private String remoteGitUrl = { require('intellij.remote.url', "Repository url") }()
  @Lazy
  private String remoteCacheUrl = { require(REMOTE_CACHE_URL_PROPERTY, "JPS remote cache url") }()
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

  /**
   * Download latest available compilation cache from remote cache and perform compilation if necessary
   */
  def warmUp() {
    def availableForHeadCommit = System.getProperty('intellij.jps.cache.availableForHeadCommit', 'false').toBoolean()
    def forceDownload = System.getProperty('intellij.jps.cache.download.force', 'false').toBoolean()
    def cacheDir = context.compilationData.dataStorageRoot
    def downloader = new CompilationOutputsDownloader(context, remoteCacheUrl, remoteGitUrl, availableForHeadCommit)
    if (forceDownload || !cacheDir.isDirectory() || !cacheDir.list()) {
      downloader.downloadCachesAndOutput()
    }
    if (!downloader.availableForHeadCommit) {
      context.options.incrementalCompilation = true
      CompilationTasks.create(context).compileAllModulesAndTests()
    }
    context.options.incrementalCompilation = false
    context.options.useCompiledClassesFromProjectOutput = true
  }

  /**
   * Upload local compilation cache to remote cache
   */
  def upload(File outputDirectoryFile) {
    def remoteCacheUrl = require('intellij.jps.remote.cache.upload.url', "JPS remote cache upload url")
    def syncFolder = require("jps.caches.aws.sync.folder", "AWS sync folder")
    def agentPersistentStorage = require("agent.persistent.cache", "Agent persistent storage")
    def commitHash = require("build.vcs.number", "Repository commit")
    def updateCommitHistory = System.getProperty('intellij.jps.remote.cache.updateHistory', 'true').toBoolean()
    context.messages.info("AWS sync folder $syncFolder")
    context.messages.info("Git remote url $remoteGitUrl")
    Map<String, String> remotePerCommitHash = [:]
    remotePerCommitHash[remoteGitUrl] = commitHash
    new CompilationOutputsUploader(
      context, remoteCacheUrl, remotePerCommitHash,
      agentPersistentStorage, syncFolder, updateCommitHistory
    ).upload(outputDirectoryFile)
  }
}
