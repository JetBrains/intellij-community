// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.openapi.util.text.StringUtil
import groovy.transform.CompileStatic
import org.jetbrains.intellij.build.CompilationContext
import org.jetbrains.intellij.build.CompilationTasks

@CompileStatic
class PortableCompilationCache {
  private final CompilationContext context

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

  private final String remoteCacheUrl = require('intellij.jps.remote.cache.url', "JPS remote cache url")
  private final String remoteGitUrl = require('intellij.remote.url', "Repository url")

  def warmUp() {
    def checkHistory = System.getProperty('intellij.jps.remote.cache.checkHistory', 'true').toBoolean()
    def forceDownload = System.getProperty('intellij.jps.cache.download.force', 'false').toBoolean()
    def cacheDir = context.compilationData.dataStorageRoot
    if (forceDownload || !cacheDir.isDirectory() || !cacheDir.list()) {
      new CompilationOutputsDownloader(context, remoteCacheUrl, remoteGitUrl, checkHistory).downloadCachesAndOutput()
    }
    if (checkHistory) {
      context.options.incrementalCompilation = true
      CompilationTasks.create(context).compileAllModulesAndTests()
    }
  }

  def upload() {
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
    ).upload()
  }
}
