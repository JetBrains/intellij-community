// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup

import com.intellij.configurationStore.checkUnknownMacros
import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

private class CheckProjectActivity : ProjectActivity {
  init {
    val app = ApplicationManager.getApplication()
    if (app.isHeadlessEnvironment || app.isUnitTestMode) {
      throw ExtensionNotApplicableException.create()
    }
  }

  override suspend fun execute(project: Project) {
    delay(30.seconds)
    checkUnknownMacros(project = project, notify = true)
    checkProjectRoots(project)
  }

  private suspend fun checkProjectRoots(project: Project) {
    val roots = project.serviceAsync<ProjectRootManager>().contentRoots
    if (roots.isEmpty()) {
      return
    }

    val watcher = (LocalFileSystem.getInstance() as? LocalFileSystemImpl)?.fileWatcher
    if (watcher == null || !watcher.isOperational) {
      return
    }

    val logger = logger<CheckProjectActivity>()
    logger.debug("FW/roots waiting started")
    while (watcher.isSettingRoots) {
      delay(10.milliseconds)
    }
    logger.debug("FW/roots waiting finished")

    val manualWatchRoots = watcher.manualWatchRoots
    if (manualWatchRoots.isNotEmpty()) {
      val unwatched = roots.filter { root -> root.isInLocalFileSystem && manualWatchRoots.any { VfsUtilCore.isAncestorOrSelf(it, root) } }
      if (unwatched.isNotEmpty()) {
        val message = IdeCoreBundle.message("watcher.non.watchable.project", ApplicationNamesInfo.getInstance().fullProductName)
        watcher.notifyOnFailure(message, null)
        logger.info("unwatched roots: ${unwatched.map { it.presentableUrl }}")
        logger.info("manual watches: ${manualWatchRoots}")
      }
    }
  }
}
