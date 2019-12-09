// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.startup

import com.intellij.configurationStore.checkUnknownMacros
import com.intellij.internal.statistic.collectors.fus.project.ProjectFsStatsCollector
import com.intellij.notification.Notification
import com.intellij.notification.NotificationListener
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionNotApplicableException
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.impl.local.LocalFileSystemImpl
import com.intellij.project.isDirectoryBased
import com.intellij.util.PathUtil
import com.intellij.util.TimeoutUtil
import com.intellij.util.concurrency.NonUrgentExecutor
import java.io.FileNotFoundException
import java.io.IOException

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
    try {
      checkFsSanity(project)
    }
    catch (e: IOException) {
      LOG.warn(e)
    }

    NonUrgentExecutor.getInstance().execute {
      checkProjectRoots(project)
      checkUnknownMacros(project, true)
    }
  }

  private fun checkFsSanity(project: Project) {
    var path = project.projectFilePath
    if (path == null || FileUtil.isAncestor(PathManager.getConfigPath(), path, true)) {
      return
    }

    if (project.isDirectoryBased) {
      path = PathUtil.getParentPath(path)
    }

    val expected = SystemInfo.isFileSystemCaseSensitive
    val actual = try {
      FileUtil.isFileSystemCaseSensitive(path)
    }
    catch (ignore: FileNotFoundException) {
      return
    }

    LOG.info("$path case-sensitivity: expected=$expected actual=$actual")
    if (actual != expected) {
      // IDE=true -> FS=false -> prefix='in'
      val prefix = if (expected) 1 else 0
      val title = ApplicationBundle.message("fs.case.sensitivity.mismatch.title")
      val text = ApplicationBundle.message("fs.case.sensitivity.mismatch.message", prefix)
      Notifications.Bus.notify(
        Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, title, text,
                     NotificationType.WARNING,
                     NotificationListener.URL_OPENING_LISTENER), project)
    }
    ProjectFsStatsCollector.caseSensitivity(project, actual)
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
      if (!manualWatchRoots.isEmpty()) {
        var nonWatched: MutableList<String>? = null
        for (root in roots) {
          if (root.fileSystem !is LocalFileSystem) {
            continue
          }

          val rootPath = root.path
          for (manualWatchRoot in manualWatchRoots) {
            if (FileUtil.isAncestor(manualWatchRoot!!, rootPath, false)) {
              if (nonWatched == null) {
                nonWatched = mutableListOf()
              }
              nonWatched.add(rootPath)
            }
          }
        }

        if (nonWatched != null) {
          val message = ApplicationBundle.message("watcher.non.watchable.project")
          watcher.notifyOnFailure(message, null)
          LOG.info("unwatched roots: $nonWatched")
          LOG.info("manual watches: $manualWatchRoots")
          pctNonWatched = (100.0 * nonWatched.size / roots.size).toInt()
        }
      }
      ProjectFsStatsCollector.watchedRoots(project, pctNonWatched)
    }
  }
}
