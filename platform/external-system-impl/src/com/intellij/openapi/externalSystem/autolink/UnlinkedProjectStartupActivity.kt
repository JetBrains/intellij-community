// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.externalSystem.autoimport.AsyncFileChangeListenerBase
import com.intellij.openapi.externalSystem.autoimport.AutoImportProjectTracker.Companion.LOG
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware.Companion.EP_NAME
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.isConfiguredByPlatformProcessor
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.isNewProject
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.AppExecutorUtil
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.asCompletableFuture
import java.util.concurrent.CompletableFuture

class UnlinkedProjectStartupActivity : StartupActivity.Background {
  private val backgroundExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("UnlinkedProjectTracker.backgroundExecutor", 1)

  override fun runActivity(project: Project) {
    val externalProjectPath = project.guessProjectDir()?.path ?: return
    showNotificationWhenNonEmptyProjectUnlinked(project)
    showNotificationWhenBuildToolPluginEnabled(project, externalProjectPath)
    showNotificationWhenNewBuildFileCreated(project, externalProjectPath)
    if (!project.isNewExternalProject()) {
      if (!project.isNewPlatformProject() && project.isOpenedWithEmptyModel()) {
        linkProjectIfUnlinkedProjectsFound(project, externalProjectPath)
      }
      else {
        showNotificationIfUnlinkedProjectsFound(project, externalProjectPath)
      }
    }
  }

  private fun Project.isNewExternalProject(): Boolean {
    return ExternalSystemUtil.isNewProject(this)
  }

  private fun Project.isNewPlatformProject(): Boolean {
    return isNewProject()
  }

  private fun Project.isOpenedWithEmptyModel(): Boolean {
    return isConfiguredByPlatformProcessor() || isEmptyModel()
  }

  private fun Project.isEmptyModel(): Boolean {
    val moduleManager = ModuleManager.getInstance(this)
    return moduleManager.modules.isEmpty()
  }

  private fun linkProjectIfUnlinkedProjectsFound(project: Project, externalProjectPath: String) {
    findUnlinkedProjectBuildFiles(project, externalProjectPath) {
      val unlinkedProjects = it.filter { (_, buildFiles) -> buildFiles.isNotEmpty() }
      val linkedProjects = it.filter { (upa, _) -> upa.isLinkedProject(project, externalProjectPath) }
      if (unlinkedProjects.size == 1 && linkedProjects.isEmpty() && !Registry.`is`("external.system.auto.import.disabled")) {
        val unlinkedProject = unlinkedProjects.keys.single()
        if (LOG.isDebugEnabled) {
          val projectId = unlinkedProject.getProjectId(externalProjectPath)
          LOG.debug("Auto-linked ${projectId.readableName} project")
        }
        unlinkedProject.linkAndLoadProjectWithLoadingConfirmation(project, externalProjectPath)
      }
      else {
        val notificationAware = UnlinkedProjectNotificationAware.getInstance(project)
        for ((unlinkedProjectAware, _) in unlinkedProjects) {
          notificationAware.notify(unlinkedProjectAware, externalProjectPath)
        }
      }
    }
  }

  private fun showNotificationIfUnlinkedProjectsFound(project: Project, externalProjectPath: String) {
    EP_NAME.forEachExtensionSafe {
      showNotificationIfUnlinkedProjectsFound(project, externalProjectPath, it)
    }
  }

  private fun showNotificationIfUnlinkedProjectsFound(project: Project, externalProjectPath: String, buildFiles: Set<VirtualFile>) {
    EP_NAME.forEachExtensionSafe {
      showNotificationIfUnlinkedProjectsFound(project, externalProjectPath, buildFiles, it)
    }
  }

  private fun showNotificationIfUnlinkedProjectsFound(
    project: Project,
    externalProjectPath: String,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware
  ) {
    showNotificationIfUnlinkedProjectsFound(project, externalProjectPath, unlinkedProjectAware) {
      unlinkedProjectAware.getBuildFiles(project, externalProjectPath)
    }
  }

  private fun showNotificationIfUnlinkedProjectsFound(
    project: Project,
    externalProjectPath: String,
    possibleBuildFiles: Set<VirtualFile>,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware
  ) {
    showNotificationIfUnlinkedProjectsFound(project, externalProjectPath, unlinkedProjectAware) {
      possibleBuildFiles.filter { unlinkedProjectAware.isBuildFile(project, it) }
    }
  }

  private fun showNotificationIfUnlinkedProjectsFound(
    project: Project,
    externalProjectPath: String,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware,
    collectBuildFiles: () -> Collection<VirtualFile>
  ) {
    findUnlinkedProjectBuildFiles(project, externalProjectPath, unlinkedProjectAware, collectBuildFiles)
      .whenComplete { buildFiles, _ ->
        if (buildFiles.isNotEmpty()) {
          val notificationAware = UnlinkedProjectNotificationAware.getInstance(project)
          notificationAware.notify(unlinkedProjectAware, externalProjectPath)
        }
      }
  }

  private fun findUnlinkedProjectBuildFiles(
    project: Project,
    externalProjectPath: String,
    callback: (Map<ExternalSystemUnlinkedProjectAware, Collection<VirtualFile>>) -> Unit
  ) {
    findUnlinkedProjectBuildFiles(project, externalProjectPath).thenAccept { callback(it.toMap()) }
  }

  private fun findUnlinkedProjectBuildFiles(
    project: Project,
    externalProjectPath: String
  ): CompletableFuture<List<Pair<ExternalSystemUnlinkedProjectAware, Collection<VirtualFile>>>> {
    return allOf(EP_NAME.extensionList.map { unlinkedProjectAware ->
      findUnlinkedProjectBuildFiles(project, externalProjectPath, unlinkedProjectAware)
        .thenApply { buildFiles -> unlinkedProjectAware to buildFiles }
    })
  }

  private fun <T> allOf(futures: Collection<CompletableFuture<T>>): CompletableFuture<List<T>> {
    return CompletableFuture.allOf(*futures.toTypedArray())
      .thenApply { futures.map { it.get() } }
  }

  private fun findUnlinkedProjectBuildFiles(
    project: Project,
    externalProjectPath: String,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware,
  ): CompletableFuture<Collection<VirtualFile>> {
    return findUnlinkedProjectBuildFiles(project, externalProjectPath, unlinkedProjectAware) {
      unlinkedProjectAware.getBuildFiles(project, externalProjectPath)
    }
  }

  private fun findUnlinkedProjectBuildFiles(
    project: Project,
    externalProjectPath: String,
    unlinkedProjectAware: ExternalSystemUnlinkedProjectAware,
    collectBuildFiles: () -> Collection<VirtualFile>
  ): CompletableFuture<Collection<VirtualFile>> {
    return if (unlinkedProjectAware.isLinkedProject(project, externalProjectPath)) {
      CompletableFuture.completedFuture(emptyList())
    }
    else {
      AsyncPromise<Collection<VirtualFile>>().asCompletableFuture().apply {
        ReadAction.nonBlocking(collectBuildFiles)
          .expireWith(createExtensionDisposable(project, unlinkedProjectAware))
          .finishOnUiThread(ModalityState.defaultModalityState()) { buildFiles ->
            if (LOG.isDebugEnabled && buildFiles.isNotEmpty()) {
              val projectId = unlinkedProjectAware.getProjectId(externalProjectPath)
              LOG.debug("Found unlinked ${projectId.readableName} project; buildFiles=${buildFiles.map(VirtualFile::getPath)}")
            }
            complete(buildFiles)
          }
          .submit(backgroundExecutor)
      }
    }
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
        showNotificationIfUnlinkedProjectsFound(project, externalProjectPath)
      }
    }, extensionDisposable)
  }

  private fun showNotificationWhenBuildToolPluginEnabled(project: Project, externalProjectPath: String) {
    EP_NAME.addExtensionPointListener(
      object : ExtensionPointListener<ExternalSystemUnlinkedProjectAware> {
        override fun extensionAdded(extension: ExternalSystemUnlinkedProjectAware, pluginDescriptor: PluginDescriptor) {
          showNotificationIfUnlinkedProjectsFound(project, externalProjectPath, extension)
        }
      }, project)
  }

  private fun showNotificationWhenNewBuildFileCreated(project: Project, externalProjectPath: String) {
    val asyncNewFilesListener = NewBuildFilesListener(project, externalProjectPath)
    val fileManager = VirtualFileManager.getInstance()
    fileManager.addAsyncFileListener(asyncNewFilesListener, project)
  }

  private fun ExternalSystemUnlinkedProjectAware.getBuildFiles(project: Project, externalProjectPath: String): List<VirtualFile> {
    val localFilesSystem = LocalFileSystem.getInstance()
    val externalProjectDir = localFilesSystem.findFileByPath(externalProjectPath)
    if (externalProjectDir == null) return emptyList()
    return externalProjectDir.children.filter { isBuildFile(project, it) }
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
      showNotificationIfUnlinkedProjectsFound(project, externalProjectPath, buildFiles)
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