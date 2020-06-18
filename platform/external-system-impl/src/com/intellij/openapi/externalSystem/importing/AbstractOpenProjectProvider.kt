// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.importing

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.autolink.UnlinkedProjectNotificationAware
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Experimental
abstract class AbstractOpenProjectProvider : OpenProjectProvider {
  protected open val systemId: ProjectSystemId? = null

  protected abstract fun isProjectFile(file: VirtualFile): Boolean

  protected abstract fun linkAndRefreshProject(projectDirectory: Path, project: Project)

  override fun canOpenProject(file: VirtualFile): Boolean {
    return if (file.isDirectory) file.children.any(::isProjectFile) else isProjectFile(file)
  }

  override fun openProject(projectFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    LOG.debug("Open project from $projectFile")
    val projectDirectory = getProjectDirectory(projectFile)
    if (focusOnOpenedSameProject(projectDirectory.toNioPath())) {
      return null
    }
    val nioPath = projectDirectory.toNioPath()
    val isValidIdeaProject = isValidProjectPath(nioPath)
    val options = OpenProjectTask(
      isNewProject = !isValidIdeaProject,
      forceOpenInNewFrame = forceOpenInNewFrame,
      projectToClose = projectToClose,
      runConfigurators = false,
      beforeOpen = { project ->
        if (isValidIdeaProject) {
          systemId?.let { UnlinkedProjectNotificationAware.enableNotifications(project, it) }
          return@OpenProjectTask true
        }
        project.putUserData(ExternalSystemDataKeys.NEWLY_IMPORTED_PROJECT, true)
        ApplicationManager.getApplication().invokeAndWait {
          linkAndRefreshProject(nioPath, project)
        }
        updateLastProjectLocation(nioPath)
        return@OpenProjectTask true
      }
    )
    return ProjectManagerEx.getInstanceEx().openProject(nioPath, options)
  }

  override fun linkToExistingProject(projectFile: VirtualFile, project: Project) {
    LOG.debug("Import project from $projectFile")
    val projectDirectory = getProjectDirectory(projectFile)
    linkAndRefreshProject(projectDirectory.toNioPath(), project)
  }

  fun linkToExistingProject(projectFilePath: String, project: Project) {
    val localFileSystem = LocalFileSystem.getInstance()
    val projectFile = localFileSystem.refreshAndFindFileByPath(projectFilePath)
    if (projectFile == null) {
      val shortPath = FileUtil.getLocationRelativeToUserHome(FileUtil.toSystemDependentName(projectFilePath), false)
      throw IllegalArgumentException(ExternalSystemBundle.message("error.project.does.not.exist", systemId?.readableName, shortPath))
    }
    linkToExistingProject(projectFile, project)
  }

  private fun focusOnOpenedSameProject(projectDirectory: Path): Boolean {
    for (project in ProjectManager.getInstance().openProjects) {
      if (isSameProject(projectDirectory, project)) {
        focusProjectWindow(project, false)
        return true
      }
    }
    return false
  }

  private fun getProjectDirectory(file: VirtualFile): VirtualFile {
    return if (file.isDirectory) file else file.parent
  }

  companion object {
    protected val LOG = logger<AbstractOpenProjectProvider>()
  }
}