// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.importing

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.autolink.ExternalSystemUnlinkedProjectAware
import com.intellij.openapi.externalSystem.autolink.UnlinkedProjectNotificationAware
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemActivityKey
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.observation.trackActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Experimental
abstract class AbstractOpenProjectProvider {

  abstract val systemId: ProjectSystemId

  protected abstract fun isProjectFile(file: VirtualFile): Boolean

  open fun canOpenProject(file: VirtualFile): Boolean {
    return if (file.isDirectory) file.children.any(::isProjectFile) else isProjectFile(file)
  }

  protected open fun getProjectDirectory(file: VirtualFile): VirtualFile {
    return if (file.isDirectory) file else file.parent
  }

  @Deprecated("use async method instead", ReplaceWith("linkToExistingProjectAsync"))
  open fun linkToExistingProject(projectFile: VirtualFile, project: Project) {
    throw UnsupportedOperationException()
  }

  suspend fun linkToExistingProjectAsync(projectFile: VirtualFile, project: Project) {
    unlinkOtherLinkedProjects(project, projectFile)
    LOG.info("Linking $systemId project ${projectFile.path}")
    linkProject(projectFile, project)
  }

  protected suspend fun unlinkOtherLinkedProjects(project: Project, projectFile: VirtualFile) {
    val externalProjectPath = if (projectFile.isDirectory) projectFile.path else projectFile.parent.path
    ExternalSystemUnlinkedProjectAware.unlinkOtherLinkedProjects(project, externalProjectPath, systemId)
  }

  open suspend fun unlinkProject(project: Project, externalProjectPath: String) {
    throw UnsupportedOperationException()
  }

  protected open suspend fun linkProject(projectFile: VirtualFile, project: Project, ) {
    withContext(Dispatchers.EDT) {
      blockingContext {
        @Suppress("DEPRECATION")
        linkToExistingProject(projectFile, project)
      }
    }
  }

  open suspend fun openProject(projectFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    LOG.debug("Open ${systemId.readableName} project from $projectFile")

    val projectDirectory = getProjectDirectory(projectFile)
    if (focusOnOpenedSameProject(projectDirectory.toNioPath())) {
      return null
    }
    val nioPath = projectDirectory.toNioPath()
    val isValidIdeaProject = ProjectUtil.isValidProjectPath(nioPath)

    val options = OpenProjectTask {
      isNewProject = !isValidIdeaProject
      this.forceOpenInNewFrame = forceOpenInNewFrame
      this.projectToClose = projectToClose
      runConfigurators = false
      beforeOpen = { project ->
        if (isValidIdeaProject) {
          UnlinkedProjectNotificationAware.enableNotifications(project, systemId)
        }
        else {
          project.putUserData(ExternalSystemDataKeys.NEWLY_CREATED_PROJECT, true)
          project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, true)
          project.trackActivity(ExternalSystemActivityKey) {
            linkToExistingProjectAsync(projectFile, project)
          }
          ProjectUtil.updateLastProjectLocation(nioPath)
        }
        true
      }
    }
    return ProjectManagerEx.getInstanceEx().openProjectAsync(nioPath, options)
  }

  @Deprecated("use async method instead", ReplaceWith("linkToExistingProjectAsync"))
  fun linkToExistingProject(projectFilePath: String, project: Project) {
    @Suppress("DEPRECATION")
    linkToExistingProject(getProjectFile(projectFilePath), project)
  }

  suspend fun linkToExistingProjectAsync(projectFilePath: String, project: Project) {
    linkToExistingProjectAsync(getProjectFile(projectFilePath), project)
  }

  protected fun getProjectFile(projectFilePath: String): VirtualFile {
    val localFileSystem = LocalFileSystem.getInstance()
    val projectFile = localFileSystem.refreshAndFindFileByPath(projectFilePath)
    if (projectFile == null) {
      val shortPath = getPresentablePath(projectFilePath)
      throw IllegalArgumentException(ExternalSystemBundle.message("error.project.does.not.exist", systemId.readableName, shortPath))
    }
    return projectFile
  }

  private fun focusOnOpenedSameProject(projectDirectory: Path): Boolean {
    for (project in ProjectManager.getInstance().openProjects) {
      if (ProjectUtil.isSameProject(projectDirectory, project)) {
        ProjectUtil.focusProjectWindow(project, false)
        return true
      }
    }
    return false
  }

  companion object {
    @JvmStatic
    protected val LOG = logger<AbstractOpenProjectProvider>()
  }
}