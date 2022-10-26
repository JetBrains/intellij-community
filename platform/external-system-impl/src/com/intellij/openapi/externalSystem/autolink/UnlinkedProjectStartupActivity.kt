// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker.Companion.LOG
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

@VisibleForTesting
class UnlinkedProjectStartupActivity : ProjectPostStartupActivity {
  override suspend fun execute(project: Project) {
    val externalProjectPath = project.guessProjectDir()?.path ?: return
    showNotificationWhenNonEmptyProjectUnlinked(project)
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
      val projects = findUnlinkedProjectBuildFiles(project, externalProjectPath)
      val linkedProjects = projects.filter { it.key.isLinkedProject(project, externalProjectPath) }
      val unlinkedProjects = projects.filter { it.key !in linkedProjects && it.value.isNotEmpty() }
      if (isExpectedAutoLink && unlinkedProjects.size == 1 && linkedProjects.isEmpty()) {
        val unlinkedProjectAware = unlinkedProjects.keys.single()
        if (LOG.isDebugEnabled) {
          val projectId = unlinkedProjectAware.getProjectId(externalProjectPath)
          LOG.debug("Auto-linked ${projectId.debugName} project")
        }
        withContext(Dispatchers.EDT) {
          unlinkedProjectAware.linkAndLoadProjectWithLoadingConfirmation(project, externalProjectPath)
        }
        return
      }
      for ((unlinkedProjectAware, buildFiles) in unlinkedProjects) {
        showUnlinkedProjectsNotification(project, externalProjectPath, unlinkedProjectAware, buildFiles)
      }
    }
  }

  private suspend fun showNotificationIfUnlinkedProjectsFound(project: Project, externalProjectPath: String) {
    forEachExtensionSafe(EP_NAME) { unlinkedProjectAware ->
      showNotificationIfUnlinkedProjectsFound(project, externalProjectPath, unlinkedProjectAware)
    }
  }

  private suspend fun showNotificationIfUnlinkedProjectsFound(
    project: Project,
    externalProjectPath: String,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware
  ) {
    val buildFiles = findUnlinkedProjectBuildFiles(project, externalProjectPath, unlinkedProjectAware)
    showUnlinkedProjectsNotification(project, externalProjectPath, unlinkedProjectAware, buildFiles)
  }

  private suspend fun showUnlinkedProjectsNotification(
    project: Project,
    externalProjectPath: String,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware,
    buildFiles: Set<VirtualFile>
  ) {
    if (buildFiles.isNotEmpty()) {
      val notificationAware = UnlinkedProjectNotificationAware.getInstance(project)
      withContext(Dispatchers.EDT) {
        notificationAware.notify(unlinkedProjectAware, externalProjectPath)
      }
    }
  }

  private suspend fun findUnlinkedProjectBuildFiles(
    project: Project,
    externalProjectPath: String
  ): Map<ExternalSystemUnlinkedProjectAware, Set<VirtualFile>> {
    return EP_NAME.extensionList.associateWith { unlinkedProjectAware ->
      findUnlinkedProjectBuildFiles(project, externalProjectPath, unlinkedProjectAware)
    }
  }

  private suspend fun findUnlinkedProjectBuildFiles(
    project: Project,
    externalProjectPath: String,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware
  ): Set<VirtualFile> {
    if (unlinkedProjectAware.isLinkedProject(project, externalProjectPath)) {
      return emptySet()
    }
    val buildFiles = readAction(project, unlinkedProjectAware) {
      unlinkedProjectAware.getBuildFiles(project, externalProjectPath)
    }
    if (LOG.isDebugEnabled && buildFiles.isNotEmpty()) {
      val projectId = unlinkedProjectAware.getProjectId(externalProjectPath)
      LOG.debug("Found unlinked ${projectId.debugName} project; buildFiles=${buildFiles.map(VirtualFile::getPath)}")
    }
    return buildFiles
  }

  private fun showNotificationWhenNonEmptyProjectUnlinked(project: Project) {
    EP_NAME.forEachExtensionSafe {
      showNotificationWhenNonEmptyProjectUnlinked(project, it)
    }
    EP_NAME.addExtensionPointListener(
      object : ExtensionPointListener<ExternalSystemUnlinkedProjectAware> {
        override fun extensionAdded(extension: ExternalSystemUnlinkedProjectAware, pluginDescriptor: PluginDescriptor) {
          showNotificationWhenNonEmptyProjectUnlinked(project, extension)
        }
      }, project)
  }

  private fun showNotificationWhenNonEmptyProjectUnlinked(project: Project, unlinkedProjectAware: ExternalSystemUnlinkedProjectAware) {
    val extensionDisposable = createExtensionDisposable(project, unlinkedProjectAware)
    unlinkedProjectAware.subscribe(project, object : ExternalSystemProjectLinkListener {
      override fun onProjectUnlinked(externalProjectPath: String) {
        project.coroutineScope.launch {
          coroutineScope(extensionDisposable) {
            showNotificationIfUnlinkedProjectsFound(project, externalProjectPath)
          }
        }
      }
    }, extensionDisposable)
  }

  private fun showNotificationWhenBuildToolPluginEnabled(project: Project, externalProjectPath: String) {
    EP_NAME.addExtensionPointListener(
      object : ExtensionPointListener<ExternalSystemUnlinkedProjectAware> {
        override fun extensionAdded(extension: ExternalSystemUnlinkedProjectAware, pluginDescriptor: PluginDescriptor) {
          val extensionDisposable = createExtensionDisposable(project, extension)
          project.coroutineScope.launch {
            coroutineScope(extensionDisposable) {
              showNotificationIfUnlinkedProjectsFound(project, externalProjectPath, extension)
            }
          }
        }
      }, project)
  }

  private fun showNotificationWhenNewBuildFileCreated(project: Project, externalProjectPath: String) {
    EP_NAME.forEachExtensionSafe {
      showNotificationWhenNewBuildFileCreated(project, externalProjectPath, it)
    }
    EP_NAME.addExtensionPointListener(
      object : ExtensionPointListener<ExternalSystemUnlinkedProjectAware> {
        override fun extensionAdded(extension: ExternalSystemUnlinkedProjectAware, pluginDescriptor: PluginDescriptor) {
          showNotificationWhenNewBuildFileCreated(project, externalProjectPath, extension)
        }
      }, project)
  }

  private fun showNotificationWhenNewBuildFileCreated(
    project: Project,
    externalProjectPath: String,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware
  ) {
    val extensionDisposable = createExtensionDisposable(project, unlinkedProjectAware)
    val listener = NewBuildFilesListener(project, externalProjectPath, unlinkedProjectAware, extensionDisposable)
    installAsyncVirtualFileListener(listener, extensionDisposable)
  }

  private fun ExternalSystemUnlinkedProjectAware.getBuildFiles(project: Project, externalProjectPath: String): Set<VirtualFile> {
    val localFilesSystem = LocalFileSystem.getInstance()
    val externalProjectDir = localFilesSystem.findFileByPath(externalProjectPath) ?: return emptySet()
    return externalProjectDir.children.filter { isBuildFile(project, it) }.toSet()
  }

  private inner class NewBuildFilesListener(
    private val project: Project,
    private val externalProjectPath: String,
    private val unlinkedProjectAware: ExternalSystemUnlinkedProjectAware,
    private val parentDisposable: Disposable
  ) : VirtualFileChangesListener {
    private lateinit var buildFiles: MutableSet<VirtualFile>

    override fun init() {
      buildFiles = HashSet()
    }

    override fun apply() {
      project.coroutineScope.launch {
        coroutineScope(parentDisposable) {
          showUnlinkedProjectsNotification(project, externalProjectPath, unlinkedProjectAware, buildFiles.toSet())
        }
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
    private suspend fun <R> readAction(project: Project, unlinkedProjectAware: ExternalSystemUnlinkedProjectAware, action: () -> R): R {
      return createExtensionDisposable(project, unlinkedProjectAware).use { disposable ->
        readAction(disposable) {
          action()
        }
      }
    }

    private suspend fun <R> readAction(parentDisposable: Disposable, action: () -> R): R {
      return coroutineScope(parentDisposable) {
        readAction {
          action()
        }
      }
    }

    private suspend fun <R> coroutineScope(parentDisposable: Disposable, action: suspend CoroutineScope.() -> R): R {
      return coroutineScope {
        Disposer.newDisposable(parentDisposable, "CoroutineScope").use { disposable ->
          val task = async(start = CoroutineStart.LAZY) {
            action()
          }
          Disposer.register(disposable, Disposable {
            task.cancel("disposed")
          })
          task.start()
          task.await()
        }
      }
    }
  }

  private inline fun <T : Any> forEachExtensionSafe(point: ExtensionPointName<T>, consumer: (T) -> Unit) {
    for (item in point.extensionList) {
      try {
        consumer(item)
      }
      catch (e: CancellationException) {
        throw e
      }
      catch (e: Exception) {
        LOG.error(e)
      }
    }
  }
}