// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointUtil
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.externalSystem.autoimport.AsyncFileChangeListenerBase
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware.Companion.EP_NAME
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.AppExecutorUtil
import java.util.concurrent.Callable

class UnlinkedProjectStartupActivity : StartupActivity.Background {
  private val backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("UnlinkedProjectTracker.backgroundExecutor", 1)

  override fun runActivity(project: Project) {
    val externalProjectPath = project.guessProjectDir()?.path ?: return
    notifyNotificationWhenNonEmptyProjectUnlinked(project)
    notifyNotificationWhenBuildToolPluginEnabled(project, externalProjectPath)
    notifyNotificationWhenNewBuildFileCreated(project, externalProjectPath)
    if (!ExternalSystemUtil.isNewProject(project)) {
      notifyNotifyNotificationIfUnlinkedProjectFound(project, externalProjectPath)
    }
  }

  private fun notifyNotifyNotificationIfUnlinkedProjectFound(project: Project, externalProjectPath: String) {
    EP_NAME.forEachExtensionSafe {
      notifyNotifyNotificationIfUnlinkedProjectFound(project, externalProjectPath, it)
    }
  }

  private fun notifyNotifyNotificationIfUnlinkedProjectFound(project: Project, externalProjectPath: String, buildFiles: Set<VirtualFile>) {
    EP_NAME.forEachExtensionSafe {
      notifyNotifyNotificationIfUnlinkedProjectFound(project, externalProjectPath, buildFiles, it)
    }
  }

  private fun notifyNotifyNotificationIfUnlinkedProjectFound(
    project: Project,
    externalProjectPath: String,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware
  ) {
    val extensionDisposable = createExtensionDisposable(project, unlinkedProjectAware)
    if (unlinkedProjectAware.isLinkedProject(project, externalProjectPath)) return
    ReadAction.nonBlocking(Callable { unlinkedProjectAware.getBuildFiles(project, externalProjectPath) })
      .expireWith(extensionDisposable)
      .finishOnUiThread(ModalityState.defaultModalityState()) { buildFiles ->
        if (buildFiles.isNotEmpty()) {
          val notificationAware = UnlinkedProjectNotificationAware.getInstance(project)
          notificationAware.notify(unlinkedProjectAware, externalProjectPath)
        }
      }
      .submit(backgroundExecutor)
  }

  private fun notifyNotifyNotificationIfUnlinkedProjectFound(
    project: Project,
    externalProjectPath: String,
    possibleBuildFiles: Set<VirtualFile>,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware
  ) {
    val extensionDisposable = createExtensionDisposable(project, unlinkedProjectAware)
    if (unlinkedProjectAware.isLinkedProject(project, externalProjectPath)) return
    ReadAction.nonBlocking(Callable { possibleBuildFiles.filter { unlinkedProjectAware.isBuildFile(project, it) } })
      .expireWith(extensionDisposable)
      .finishOnUiThread(ModalityState.defaultModalityState()) { buildFiles ->
        if (buildFiles.isNotEmpty()) {
          val notificationAware = UnlinkedProjectNotificationAware.getInstance(project)
          notificationAware.notify(unlinkedProjectAware, externalProjectPath)
        }
      }
      .submit(backgroundExecutor)
  }

  private fun notifyNotificationWhenNonEmptyProjectUnlinked(project: Project) {
    EP_NAME.forEachExtensionSafe {
      notifyNotificationWhenNonEmptyProjectUnlinked(project, it)
    }
    EP_NAME.addExtensionPointListener(
      object : ExtensionPointListener<ExternalSystemUnlinkedProjectAware> {
        override fun extensionAdded(extension: ExternalSystemUnlinkedProjectAware, pluginDescriptor: PluginDescriptor) {
          notifyNotificationWhenNonEmptyProjectUnlinked(project, extension)
        }
      }, project)
  }

  private fun notifyNotificationWhenNonEmptyProjectUnlinked(project: Project, unlinkedProjectAware: ExternalSystemUnlinkedProjectAware) {
    val extensionDisposable = createExtensionDisposable(project, unlinkedProjectAware)
    unlinkedProjectAware.subscribe(project, object : ExternalSystemProjectListener {
      override fun onProjectUnlinked(externalProjectPath: String) {
        notifyNotifyNotificationIfUnlinkedProjectFound(project, externalProjectPath)
      }
    }, extensionDisposable)
  }

  private fun notifyNotificationWhenBuildToolPluginEnabled(project: Project, externalProjectPath: String) {
    EP_NAME.addExtensionPointListener(
      object : ExtensionPointListener<ExternalSystemUnlinkedProjectAware> {
        override fun extensionAdded(extension: ExternalSystemUnlinkedProjectAware, pluginDescriptor: PluginDescriptor) {
          notifyNotifyNotificationIfUnlinkedProjectFound(project, externalProjectPath, extension)
        }
      }, project)
  }

  private fun notifyNotificationWhenNewBuildFileCreated(project: Project, externalProjectPath: String) {
    val asyncNewFilesListener = NewBuildFilesListener(project, externalProjectPath)
    val fileManager = VirtualFileManager.getInstance()
    fileManager.addAsyncFileListener(asyncNewFilesListener, project)
  }

  private fun ExternalSystemUnlinkedProjectAware.getBuildFiles(project: Project, externalProjectPath: String): Set<String> {
    ProgressManager.checkCanceled()
    val localFilesSystem = LocalFileSystem.getInstance()
    val externalProjectDir = localFilesSystem.findFileByPath(externalProjectPath)
    if (externalProjectDir == null) return emptySet()
    return externalProjectDir.children.asSequence()
      .filter { isBuildFile(project, it) }
      .map { it.path }
      .toSet()
  }

  private fun createExtensionDisposable(project: Project, unlinkedProjectAware: ExternalSystemUnlinkedProjectAware): Disposable {
    return ExtensionPointUtil.createExtensionDisposable(unlinkedProjectAware, EP_NAME)
      .also { Disposer.register(project, it) }
  }

  inner class NewBuildFilesListener(
    private val project: Project,
    private val externalProjectPath: String
  ) : AsyncFileChangeListenerBase() {
    private lateinit var buildFiles: MutableSet<VirtualFile>

    override fun init() {
      buildFiles = HashSet()
    }

    override fun apply() {
      if (buildFiles.isEmpty()) return
      notifyNotifyNotificationIfUnlinkedProjectFound(project, externalProjectPath, buildFiles)
    }

    override fun isRelevant(file: VirtualFile, event: VFileEvent): Boolean =
      event is VFileCreateEvent &&
      FileUtil.pathsEqual(PathUtil.getParentPath(file.path), externalProjectPath) &&
      EP_NAME.extensionList.any { it.isBuildFile(project, file) }

    override fun updateFile(file: VirtualFile, event: VFileEvent) {
      buildFiles.add(file)
    }
  }
}