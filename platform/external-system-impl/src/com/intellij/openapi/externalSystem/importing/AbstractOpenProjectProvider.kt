// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.externalSystem.importing

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.externalSystem.ExternalSystemManager
import com.intellij.openapi.externalSystem.autolink.UnlinkedProjectNotificationAware
import com.intellij.openapi.externalSystem.model.ExternalSystemDataKeys
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.lang.StringUtils
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Experimental
abstract class AbstractOpenProjectProvider : OpenProjectProvider {
  protected open val systemId: ProjectSystemId by lazy {
    /**
     * Tries to resolve external system id
     * Note: Implemented approach is super heuristics.
     * Please, override [systemId] to avoid discrepancy with real id.
     */
    LOG.warn("Class ${javaClass.name} have to override AbstractOpenProjectProvider.systemId. " +
             "Resolving of systemId will be removed in future releases.")
    val readableName = StringUtils.splitByCharacterTypeCamelCase(javaClass.simpleName).first()
    val manager = ExternalSystemManager.EP_NAME.findFirstSafe {
      StringUtils.equalsIgnoreCase(StringUtils.splitByCharacterTypeCamelCase(it.javaClass.simpleName).first(), readableName)
    }
    manager?.systemId ?: ProjectSystemId(readableName.toUpperCase())
  }

  protected abstract fun isProjectFile(file: VirtualFile): Boolean

  @Deprecated("redundant method", replaceWith = ReplaceWith("linkToExistingProject(projectFile, project)"))
  protected open fun linkAndRefreshProject(projectDirectory: Path, project: Project) {
    throw UnsupportedOperationException("use linkToExistingProject(VirtualFile, Project) instead")
  }

  override fun canOpenProject(file: VirtualFile): Boolean {
    return if (file.isDirectory) file.children.any(::isProjectFile) else isProjectFile(file)
  }

  override suspend fun openProject(projectFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    LOG.debug("Open project from $projectFile")
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
          withContext(Dispatchers.EDT) {
            linkToExistingProject(projectFile, project)
          }
          ProjectUtil.updateLastProjectLocation(nioPath)
        }
        true
      }
    }
    return ProjectManagerEx.getInstanceEx().openProjectAsync(nioPath, options)
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
      val shortPath = getPresentablePath(projectFilePath)
      throw IllegalArgumentException(ExternalSystemBundle.message("error.project.does.not.exist", systemId.readableName, shortPath))
    }
    linkToExistingProject(projectFile, project)
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

  private fun getProjectDirectory(file: VirtualFile): VirtualFile {
    return if (file.isDirectory) file else file.parent
  }

  companion object {
    protected val LOG = logger<AbstractOpenProjectProvider>()
  }
}