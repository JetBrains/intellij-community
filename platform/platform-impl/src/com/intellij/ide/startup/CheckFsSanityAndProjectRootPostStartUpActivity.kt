// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup

import com.intellij.configurationStore.checkUnknownMacros
import com.intellij.internal.statistic.collectors.fus.project.ProjectFsStatsCollector
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.NonUrgentExecutor

internal class CheckFsSanityAndProjectRootPostStartUpActivity : StartupActivity.DumbAware {
  companion object {
    private val LOG = logger<CheckFsSanityAndProjectRootPostStartUpActivity>()
  }

  init {
    if (ApplicationManager.getApplication().isHeadlessEnvironment || ApplicationManager.getApplication().isUnitTestMode) {
      throw ExtensionNotApplicableException.INSTANCE
    }
  }

  override fun runActivity(project: Project) {
    NonUrgentExecutor.getInstance().execute {
      checkProjectRoots(project)
      checkUnknownMacros(project, true)
    }
  }

  private fun checkProjectRoots(project: Project) {
    val roots = ProjectRootManager.getInstance(project).contentRoots
    if (roots.isEmpty()) {
      return
    }

    val watcher = (LocalFileSystem.getInstance() as? LocalFileSystemImpl ?: return).fileWatcher
    if (!watcher.isOperational) {
      ProjectFsStatsCollector.watchedRoots(project, -1)
      return
    }

    ApplicationManager.getApplication().executeOnPooledThread {
      LOG.debug("FW/roots waiting started")
      while (true) {
        if (project.isDisposed) {
          return@executeOnPooledThread
        }

        if (!watcher.isSettingRoots) {
          break
        }
        TimeoutUtil.sleep(10)
      }

      LOG.debug("FW/roots waiting finished")
      val manualWatchRoots = watcher.manualWatchRoots
      var pctNonWatched = 0
      if (manualWatchRoots.isNotEmpty()) {
        val unwatched = roots.filter { root -> root.isInLocalFileSystem && manualWatchRoots.any { VfsUtilCore.isAncestorOrSelf(it, root) } }
        if (unwatched.isNotEmpty()) {
          val message = ApplicationBundle.message("watcher.non.watchable.project", ApplicationNamesInfo.getInstance().fullProductName)
          watcher.notifyOnFailure(message, null)
          LOG.info("unwatched roots: ${unwatched.map { it.presentableUrl }}")
          LOG.info("manual watches: ${manualWatchRoots}")
          pctNonWatched = (100.0 * unwatched.size / roots.size).toInt()
        }
      }
      ProjectFsStatsCollector.watchedRoots(project, pctNonWatched)
    }
  }
}
