// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.createExtensionDisposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener.Companion.installAsyncVirtualFileListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware.Companion.EP_NAME
import com.intellij.openapi.externalSystem.util.ExternalSystemActivityKey
import com.intellij.openapi.externalSystem.util.ExternalSystemInProgressService
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.io.toNioPathOrNull
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.newvfs.events.VFileContentChangeEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.toNioPathOrNull
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.isConfiguredByPlatformProcessor
import com.intellij.platform.PlatformProjectOpenProcessor.Companion.isNewProject
import com.intellij.platform.backend.observation.trackActivity
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.CopyOnWriteArrayList

@VisibleForTesting
@ApiStatus.Internal
class UnlinkedProjectStartupActivity : ProjectActivity {

  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(val coroutineScope: CoroutineScope) {
    companion object {
      fun getCoroutineScope(project: Project): CoroutineScope {
        return project.service<CoroutineScopeService>().coroutineScope
      }
    }
  }

  override suspend fun execute(project: Project) {
    project.trackActivity(ExternalSystemActivityKey) {
      project.serviceAsync<ExternalSystemInProgressService>().unlinkedActivityStarted()
      loadProjectIfSingleUnlinkedProjectFound(project)
      val projectRoots = installProjectRootsScanner(project)
      installUnlinkedProjectScanner(project, projectRoots)
    }
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

  private suspend fun loadProjectIfSingleUnlinkedProjectFound(project: Project) {
    val externalProjectPath = project.basePath ?: return
    val isNewExternalProject = isNewExternalProject(project)
    val isEnabledAutoLink = isEnabledAutoLink(project)
    val isNewPlatformProject = isNewPlatformProject(project)
    val isOpenedWithEmptyModel = isOpenedWithEmptyModel(project)
    val isExpectedAutoLink = isEnabledAutoLink && !isNewPlatformProject && isOpenedWithEmptyModel
    val (linkedProjects, unlinkedProjects) = findLinkedAndUnlinkedProjects(project, externalProjectPath)
    if (!isNewExternalProject) {
      if (isExpectedAutoLink && unlinkedProjects.size == 1 && linkedProjects.isEmpty()) {
        val extension = unlinkedProjects.single()
        extension.linkAndLoadProjectAsync(project, externalProjectPath)
        if (LOG.isDebugEnabled) {
          val projectId = extension.createProjectId(externalProjectPath)
          LOG.debug(projectId.debugName + ": project is auto-linked")
        }
        return
      }
    }
    if (LOG.isDebugEnabled) {
      LOG.debug("""
          |Project '${project.name}' wasn't auto-linked:
          |  linkedProjects=$linkedProjects
          |  unlinkedProjects=$unlinkedProjects
          |  isNewExternalProject=$isNewExternalProject
          |  isExpectedAutoLink=$isExpectedAutoLink
          |    isEnabledAutoLink=$isEnabledAutoLink
          |    isNewPlatformProject=$isNewPlatformProject
          |    isOpenedWithEmptyModel=$isOpenedWithEmptyModel
        """.trimMargin())
    }
  }

  private suspend fun findLinkedAndUnlinkedProjects(
    project: Project,
    externalProjectPath: String
  ): Pair<List<ExternalSystemUnlinkedProjectAware>, List<ExternalSystemUnlinkedProjectAware>> {
    val linkedProjects = ArrayList<ExternalSystemUnlinkedProjectAware>()
    val unlinkedProjects = ArrayList<ExternalSystemUnlinkedProjectAware>()
    EP_NAME.forEachExtensionSafeAsync { extension ->
      when {
        extension.isLinkedProject(project, externalProjectPath) ->
          linkedProjects.add(extension)
        extension.hasBuildFiles(project, externalProjectPath) ->
          unlinkedProjects.add(extension)
      }
    }
    return linkedProjects to unlinkedProjects
  }

  private suspend fun installProjectRootsScanner(project: Project): ProjectRoots {
    val projectRoots = ProjectRoots()
    val rootProjectPath = project.basePath
    if (rootProjectPath != null && !hasLinkedProject(project, rootProjectPath)) {
      projectRoots.addProjectRoot(rootProjectPath)
    }
    val coroutineScope = CoroutineScopeService.getCoroutineScope(project)
    EP_NAME.withEachExtensionSafeAsync(project) { extension, extensionDisposable ->
      extension.subscribe(project, object : ExternalSystemProjectLinkListener {

        override fun onProjectLinked(externalProjectPath: String) {
          coroutineScope.launch(extensionDisposable) {
            projectRoots.removeProjectRoot(externalProjectPath)
          }
        }

        override fun onProjectUnlinked(externalProjectPath: String) {
          coroutineScope.launch(extensionDisposable) {
            if (!hasLinkedProject(project, externalProjectPath)) {
              projectRoots.addProjectRoot(externalProjectPath)
            }
          }
        }

      }, extensionDisposable)
    }
    return projectRoots
  }

  private suspend fun installUnlinkedProjectScanner(project: Project, projectRoots: ProjectRoots) {
    whenProjectRootsChanged(project, projectRoots) { changedRoots ->
      project.trackActivity(ExternalSystemActivityKey) {
        for (projectRoot in changedRoots) {
          EP_NAME.forEachExtensionSafeAsync { extension ->
            updateNotification(project, projectRoot, extension)
          }
        }
      }
    }
    projectRoots.withProjectRoot(project) { projectRoot ->
      project.trackActivity(ExternalSystemActivityKey) {
        EP_NAME.forEachExtensionSafeAsync { extension ->
          updateNotification(project, projectRoot, extension)
        }
      }
    }
    projectRoots.whenProjectRootRemoved(project) { projectRoot ->
      project.trackActivity(ExternalSystemActivityKey) {
        EP_NAME.forEachExtensionSafeAsync { extension ->
          expireNotification(project, projectRoot, extension)
        }
      }
    }
  }

  private fun hasLinkedProject(project: Project, projectRoot: String): Boolean {
    EP_NAME.forEachExtensionSafeAsync { extension ->
      if (extension.isLinkedProject(project, projectRoot)) {
        return true
      }
    }
    return false
  }

  private suspend fun updateNotification(project: Project, projectRoot: String, extension: ExternalSystemUnlinkedProjectAware) {
    when {
      extension.isLinkedProject(project, projectRoot) ->
        expireNotification(project, projectRoot, extension)
      extension.hasBuildFiles(project, projectRoot) ->
        notifyNotification(project, projectRoot, extension)
      else ->
        expireNotification(project, projectRoot, extension)
    }
  }

  private suspend fun notifyNotification(
    project: Project,
    externalProjectPath: String,
    extension: ExternalSystemUnlinkedProjectAware
  ) {
    blockingContext {
      val extensionDisposable = EP_NAME.createExtensionDisposable(extension, project)
      UnlinkedProjectNotificationAware.getInstance(project)
        .notificationNotify(extension.createProjectId(externalProjectPath)) {
          val coroutineScope = CoroutineScopeService.getCoroutineScope(project)
          coroutineScope.launch(extensionDisposable) {
            project.trackActivity(ExternalSystemActivityKey) {
              extension.linkAndLoadProjectAsync(project, externalProjectPath)
            }
          }
        }
    }
  }

  private suspend fun expireNotification(
    project: Project,
    externalProjectPath: String,
    extension: ExternalSystemUnlinkedProjectAware
  ) {
    blockingContext {
      UnlinkedProjectNotificationAware.getInstance(project)
        .notificationExpire(extension.createProjectId(externalProjectPath))
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun whenProjectRootsChanged(
    project: Project,
    projectRoots: ProjectRoots,
    action: suspend (Set<String>) -> Unit
  ) {
    val coroutineScope = CoroutineScopeService.getCoroutineScope(project)
    val virtualFileDispatcher = Dispatchers.Default.limitedParallelism(1)
    val listener = UnlinkedProjectWatcher(projectRoots) { changedRoots ->
      coroutineScope.launch(virtualFileDispatcher) {
        action(changedRoots)
      }
    }
    installAsyncVirtualFileListener(listener, project)
  }

  private class UnlinkedProjectWatcher(
    private val projectRoots: ProjectRoots,
    private val action: (Set<String>) -> Unit
  ) : VirtualFileChangesListener {

    @Volatile
    private var changedRoots = HashSet<String>()

    override fun init() {
      changedRoots = HashSet()
    }

    override fun isRelevant(file: VirtualFile, event: VFileEvent): Boolean {
      return event !is VFileContentChangeEvent
    }

    override fun updateFile(file: VirtualFile, event: VFileEvent) {
      for (projectRoot in projectRoots) {
        val path = file.toNioPathOrNull() ?: continue
        val projectPath = projectRoot.toNioPathOrNull() ?: continue
        if (projectPath.startsWith(path)) {
          changedRoots.add(projectRoot)
        }
        else if (file.isFile && path.parent == projectPath) {
          changedRoots.add(projectRoot)
        }
      }
    }

    override fun apply() {
      val changedRoots = changedRoots.toSet()
      if (changedRoots.isNotEmpty()) {
        action(changedRoots)
      }
    }
  }

  private class ProjectRoots : Iterable<String> {

    private val projectRoots = CopyOnWriteArrayList<String>()
    private val addListeners = DisposableWrapperList<suspend (String) -> Unit>()
    private val removeListeners = DisposableWrapperList<suspend (String) -> Unit>()

    override fun iterator(): Iterator<String> {
      return projectRoots.iterator()
    }

    suspend fun addProjectRoot(projectRoot: String) {
      projectRoots.add(projectRoot)
      addListeners.forEach { it(projectRoot) }
    }

    suspend fun removeProjectRoot(projectRoot: String) {
      projectRoots.remove(projectRoot)
      removeListeners.forEach { it(projectRoot) }
    }

    fun whenProjectRootAdded(parentDisposable: Disposable, action: suspend (String) -> Unit) {
      addListeners.add(action, parentDisposable)
    }

    fun whenProjectRootRemoved(parentDisposable: Disposable, action: suspend (String) -> Unit) {
      removeListeners.add(action, parentDisposable)
    }

    suspend fun withProjectRoot(parentDisposable: Disposable, action: suspend (String) -> Unit) {
      for (projectRoot in projectRoots) {
        action(projectRoot)
      }
      whenProjectRootAdded(parentDisposable) { projectRoot ->
        action(projectRoot)
      }
    }
  }

  private fun ExternalSystemUnlinkedProjectAware.createProjectId(externalProjectPath: String): ExternalSystemProjectId {
    return ExternalSystemProjectId(systemId, externalProjectPath)
  }

  private suspend fun ExternalSystemUnlinkedProjectAware.hasBuildFiles(project: Project, externalProjectPath: String): Boolean {
    return readAction {
      val projectRoot = LocalFileSystem.getInstance().findFileByPath(externalProjectPath)
      projectRoot?.children?.firstOrNull { isBuildFile(project, it) } != null
    }
  }

  companion object {
    private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autolink")
  }
}
