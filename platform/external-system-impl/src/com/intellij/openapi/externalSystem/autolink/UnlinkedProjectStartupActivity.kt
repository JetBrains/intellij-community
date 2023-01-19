// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.extensions.*
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker.Companion.LOG
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener.Companion.installAsyncVirtualFileListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware.Companion.EP_NAME
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectPostStartupActivity
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.use
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.isConfiguredByPlatformProcessor
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.isNewProject
import com.intellij.util.PathUtil
import kotlinx.coroutines.*
import org.jetbrains.annotations.VisibleForTesting
import kotlin.coroutines.EmptyCoroutineContext

@VisibleForTesting
class UnlinkedProjectStartupActivity : ProjectPostStartupActivity {

  override suspend fun execute(project: Project) {
    val externalProjectPath = project.guessProjectDir()?.path ?: return
    showNotificationWhenNonEmptyProjectUnlinked(project)
    expireNotificationWhenProjectLinked(project)
    showNotificationWhenBuildToolPluginEnabled(project, externalProjectPath)
    showNotificationWhenNewBuildFileCreated(project, externalProjectPath)
    linkAndLoadProjectIfUnlinkedProjectsFound(project, externalProjectPath)
  }

  private fun isEnabledAutoLink(project: Project): Boolean {
    return ExternalSystemUnlinkedProjectSettings.getInstance(project).isEnabledAutoLink &&
           !Registry.`is`("external.system.auto.import.disabled")
  }

  private fun isNewExternalProject(project: Project): Boolean {
    return ExternalSystemUtil.isNewProject(project)
  }

  private fun isNewPlatformProject(project: Project): Boolean {
    return project.isNewProject()
  }

  private fun isOpenedWithEmptyModel(project: Project): Boolean {
    return project.isConfiguredByPlatformProcessor() || isEmptyModel(project)
  }

  private fun isEmptyModel(project: Project): Boolean {
    val moduleManager = ModuleManager.getInstance(project)
    return moduleManager.modules.isEmpty()
  }

  private suspend fun linkAndLoadProjectIfUnlinkedProjectsFound(project: Project, externalProjectPath: String) {
    if (!isNewExternalProject(project)) {
      val isExpectedAutoLink = isEnabledAutoLink(project) && !isNewPlatformProject(project) && isOpenedWithEmptyModel(project)
      val (linkedProjects, unlinkedProjects) = findLinkedAndUnlinkedProjects(project, externalProjectPath)
      if (isExpectedAutoLink && unlinkedProjects.size == 1 && linkedProjects.isEmpty()) {
        val unlinkedProjectAware = unlinkedProjects.single()
        if (LOG.isDebugEnabled) {
          val projectId = unlinkedProjectAware.createProjectId(externalProjectPath)
          LOG.debug("Auto-linked ${projectId.debugName} project")
        }
        withContext(Dispatchers.EDT) {
          unlinkedProjectAware.linkAndLoadProject(project, externalProjectPath)
        }
        return
      }
      for (unlinkedProjectAware in unlinkedProjects) {
        showUnlinkedProjectsNotification(project, externalProjectPath, unlinkedProjectAware)
      }
    }
  }

  private suspend fun findLinkedAndUnlinkedProjects(
    project: Project,
    externalProjectPath: String
  ): Pair<List<ExternalSystemUnlinkedProjectAware>, List<ExternalSystemUnlinkedProjectAware>> {
    val linkedProjects = ArrayList<ExternalSystemUnlinkedProjectAware>()
    val unlinkedProjects = ArrayList<ExternalSystemUnlinkedProjectAware>()
    EP_NAME.forEachExtensionSafe(project) { extension, extensionDisposable ->
      coroutineScope(extensionDisposable) {
        when {
          extension.isLinkedProject(project, externalProjectPath) ->
            linkedProjects.add(extension)
          extension.hasBuildFiles(project, externalProjectPath) ->
            unlinkedProjects.add(extension)
        }
      }
    }
    return linkedProjects to unlinkedProjects
  }

  private suspend fun showNotificationIfUnlinkedProjectsFound(
    project: Project,
    externalProjectPath: String,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware
  ) {
    val isLinked = unlinkedProjectAware.isLinkedProject(project, externalProjectPath)
    val hasBuildFiles = unlinkedProjectAware.hasBuildFiles(project, externalProjectPath)
    if (!isLinked && hasBuildFiles) {
      showUnlinkedProjectsNotification(project, externalProjectPath, unlinkedProjectAware)
    }
  }

  private fun showUnlinkedProjectsNotification(
    project: Project,
    externalProjectPath: String,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware
  ) {
    UnlinkedProjectNotificationAware.getInstance(project)
      .notificationNotify(unlinkedProjectAware.createProjectId(externalProjectPath)) {
        unlinkedProjectAware.linkAndLoadProject(project, externalProjectPath)
      }
  }

  private fun showNotificationWhenNonEmptyProjectUnlinked(project: Project) {
    EP_NAME.withEachExtensionSafe(project) { extension, extensionDisposable ->
      extension.subscribe(project, object : ExternalSystemProjectLinkListener {
        override fun onProjectUnlinked(externalProjectPath: String) {
          submit(extensionDisposable) {
            showNotificationIfUnlinkedProjectsFound(project, externalProjectPath, extension)
          }
        }
      }, extensionDisposable)
    }
  }

  private fun expireNotificationWhenProjectLinked(project: Project) {
    EP_NAME.withEachExtensionSafe(project) { extension, extensionDisposable ->
      extension.subscribe(project, object : ExternalSystemProjectLinkListener {
        override fun onProjectLinked(externalProjectPath: String) {
          UnlinkedProjectNotificationAware.getInstance(project)
            .notificationExpire(extension.createProjectId(externalProjectPath))
        }
      }, extensionDisposable)
    }
  }

  private fun showNotificationWhenBuildToolPluginEnabled(project: Project, externalProjectPath: String) {
    EP_NAME.whenExtensionAdded(project) { extension, extensionDisposable ->
      submit(extensionDisposable) {
        showNotificationIfUnlinkedProjectsFound(project, externalProjectPath, extension)
      }
    }
  }

  private fun showNotificationWhenNewBuildFileCreated(project: Project, externalProjectPath: String) {
    EP_NAME.withEachExtensionSafe(project) { extension, extensionDisposable ->
      val listener = NewBuildFilesListener(project, externalProjectPath, extension)
      installAsyncVirtualFileListener(listener, extensionDisposable)
    }
  }

  private inner class NewBuildFilesListener(
    private val project: Project,
    private val externalProjectPath: String,
    private val unlinkedProjectAware: ExternalSystemUnlinkedProjectAware
  ) : VirtualFileChangesListener {

    private lateinit var buildFiles: MutableSet<VirtualFile>

    override fun isProcessRecursively() = true

    override fun init() {
      buildFiles = HashSet()
    }

    override fun apply() {
      if (buildFiles.isNotEmpty()) {
        showUnlinkedProjectsNotification(project, externalProjectPath, unlinkedProjectAware)
      }
    }

    override fun isRelevant(file: VirtualFile, event: VFileEvent): Boolean {
      return event is VFileCreateEvent &&
             FileUtil.pathsEqual(PathUtil.getParentPath(file.path), externalProjectPath) &&
             unlinkedProjectAware.isBuildFile(project, file)
    }

    override fun updateFile(file: VirtualFile, event: VFileEvent) {
      buildFiles.add(file)
    }
  }

  companion object {

    private fun ExternalSystemUnlinkedProjectAware.createProjectId(externalProjectPath: String): ExternalSystemProjectId {
      return ExternalSystemProjectId(systemId, externalProjectPath)
    }

    private suspend fun ExternalSystemUnlinkedProjectAware.hasBuildFiles(project: Project, externalProjectPath: String): Boolean {
      val buildFile = readAction {
        LocalFileSystem.getInstance().findFileByPath(externalProjectPath)
          ?.children?.firstOrNull { isBuildFile(project, it) }
      }
      if (LOG.isDebugEnabled && buildFile != null) {
        val projectId = createProjectId(externalProjectPath)
        LOG.debug("Found unlinked ${projectId.debugName} project; buildFile=$buildFile")
      }
      return buildFile != null
    }

    private suspend fun <R> coroutineScope(parentDisposable: Disposable, action: suspend CoroutineScope.() -> R): R {
      val task = submit(parentDisposable, action)
      return task.await()
    }

    private fun <R> submit(parentDisposable: Disposable, action: suspend CoroutineScope.() -> R): Deferred<R> {
      val coroutineScope = CoroutineScope(EmptyCoroutineContext)
      val disposable = Disposer.newDisposable(parentDisposable, "CoroutineScope")
      val task = coroutineScope.async(start = CoroutineStart.LAZY) {
        disposable.use {
          action()
        }
      }
      Disposer.register(disposable, Disposable {
        task.cancel("disposed")
      })
      task.start()
      return task
    }
  }
}