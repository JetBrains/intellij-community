// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.autolink

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.extensions.createExtensionDisposable
import com.intellij.openapi.externalSystem.autoimport.ExternalSystemProjectId
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener
import com.intellij.openapi.externalSystem.autoimport.changes.vfs.VirtualFileChangesListener.Companion.installAsyncVirtualFileListener
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware.Companion.EP_NAME
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.guessProjectDir
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.io.isAncestor
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
import com.intellij.util.containers.DisposableWrapperList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import org.jetbrains.annotations.VisibleForTesting
import java.util.concurrent.CopyOnWriteArrayList

@VisibleForTesting
class UnlinkedProjectStartupActivity : ProjectActivity {
  @Service(Service.Level.PROJECT)
  private class CoroutineScopeService(val coroutineScope: CoroutineScope)

  override suspend fun execute(project: Project) {
    loadProjectIfSingleUnlinkedProjectFound(project)
    val projectRoots = installProjectRootsScanner(project)
    installUnlinkedProjectScanner(project, projectRoots)
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
    val externalProjectPath = project.guessProjectDir()?.path ?: return
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
    val rootProjectPath = project.guessProjectDir()?.path
    if (rootProjectPath != null) {
      projectRoots.addProjectRoot(rootProjectPath)
    }
    val cs = project.getService(CoroutineScopeService::class.java).coroutineScope
    EP_NAME.withEachExtensionSafeAsync(project) { extension, extensionDisposable ->
      extension.subscribe(project, object : ExternalSystemProjectLinkListener {

        override fun onProjectLinked(externalProjectPath: String) {
          cs.launch(extensionDisposable) {
            projectRoots.removeProjectRoot(externalProjectPath)
          }
        }

        override fun onProjectUnlinked(externalProjectPath: String) {
          cs.launch(extensionDisposable) {
            projectRoots.addProjectRoot(externalProjectPath)
          }
        }

      }, extensionDisposable)
    }
    return projectRoots
  }

  private suspend fun installUnlinkedProjectScanner(project: Project, projectRoots: ProjectRoots) {
    whenProjectRootsChanged(projectRoots, project) { changedRoots ->
      EP_NAME.forEachExtensionSafeAsync { extension ->
        for (projectRoot in changedRoots) {
          updateNotification(project, projectRoot, extension)
        }
      }
    }
    EP_NAME.withEachExtensionSafeAsync(project) { extension, extensionDisposable ->
      projectRoots.withProjectRoot(extensionDisposable) { projectRoot ->
        updateNotification(project, projectRoot, extension)
      }
      projectRoots.whenProjectRootRemoved(extensionDisposable) { projectRoot ->
        expireNotification(project, projectRoot, extension)
      }
    }
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
          val cs = project.getService(CoroutineScopeService::class.java).coroutineScope
          cs.launch(extensionDisposable) {
            extension.linkAndLoadProjectAsync(project, externalProjectPath)
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
    projectRoots: ProjectRoots,
    parentDisposable: Disposable,
    action: suspend (Set<String>) -> Unit
  ) {
    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1))
    val listener = UnlinkedProjectWatcher(projectRoots) { changedRoots ->
      backgroundScope.launch(parentDisposable) {
        action(changedRoots)
      }
    }
    installAsyncVirtualFileListener(listener, parentDisposable)
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
        if (path.isAncestor(projectPath, false)) {
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

  companion object {

    private val LOG = Logger.getInstance("#com.intellij.openapi.externalSystem.autolink")

    private fun ExternalSystemUnlinkedProjectAware.createProjectId(externalProjectPath: String): ExternalSystemProjectId {
      return ExternalSystemProjectId(systemId, externalProjectPath)
    }

    private suspend fun ExternalSystemUnlinkedProjectAware.hasBuildFiles(project: Project, externalProjectPath: String): Boolean {
      return readAction {
        val projectRoot = LocalFileSystem.getInstance().findFileByPath(externalProjectPath)
        projectRoot?.children?.firstOrNull { isBuildFile(project, it) } != null
      }
    }
  }
}