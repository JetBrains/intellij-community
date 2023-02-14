// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.project.ex

import com.intellij.ide.impl.OpenProjectTask
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.SystemInfoRt
import org.jetbrains.annotations.ApiStatus.Experimental
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import java.nio.file.Path

abstract class ProjectManagerEx : ProjectManager() {
  enum class PerProjectState {
    DISABLED,
    READY,
    ENABLED,
  }

  companion object {
    const val PER_PROJECT_OPTION_NAME: String = "ide.per.project.instance"

    @JvmField
    @Experimental
    val IS_PER_PROJECT_INSTANCE_READY: Boolean = System.getProperty(PER_PROJECT_OPTION_NAME)?.let {
      (SystemInfoRt.isMac || SystemInfoRt.isLinux) && PerProjectState.valueOf(it) != PerProjectState.DISABLED
    } ?: false

    @JvmField
    @Experimental
    val IS_PER_PROJECT_INSTANCE_ENABLED: Boolean = System.getProperty(PER_PROJECT_OPTION_NAME)?.let {
      IS_PER_PROJECT_INSTANCE_READY && PerProjectState.valueOf(it) == PerProjectState.ENABLED
    } ?: false

    val IS_CHILD_PROCESS: Boolean by lazy { isChildProcessPath(PathManager.getSystemDir()) }

    @Experimental
    const val PER_PROJECT_SUFFIX = "INTERNAL_perProject"

    @JvmStatic
    fun getInstanceEx(): ProjectManagerEx = ApplicationManager.getApplication().getService(ProjectManager::class.java) as ProjectManagerEx

    @JvmStatic
    fun getInstanceExIfCreated(): ProjectManagerEx? = getInstanceIfCreated() as ProjectManagerEx?

    @Internal
    fun getOpenProjects(): List<Project> {
      val projectManager = getInstanceIfCreated()
      return projectManager?.openProjects?.toList() ?: emptyList()
    }

    @Experimental
    fun isChildProcessPath(path: Path): Boolean = path.toString().contains(PER_PROJECT_SUFFIX)
  }

  @Suppress("UNUSED_PARAMETER")
  @Deprecated("Use {@link #newProject(Path, OpenProjectTask)}", level = DeprecationLevel.ERROR)
  fun newProject(projectName: String?, filePath: String, useDefaultProjectSettings: Boolean, isDummy: Boolean): Project? {
    return newProject(Path.of(filePath), OpenProjectTask {
      isNewProject = true
      this.useDefaultProjectAsTemplate = useDefaultProjectSettings
      this.projectName = projectName
    })
  }

  /**
   * Creates project but not open it. Use this method only in a test mode or special cases like new project wizard.
   */
  abstract fun newProject(file: Path, options: OpenProjectTask): Project?

  @Internal
  abstract suspend fun newProjectAsync(file: Path, options: OpenProjectTask): Project

  abstract fun openProject(projectStoreBaseDir: Path, options: OpenProjectTask): Project?

  abstract suspend fun openProjectAsync(projectStoreBaseDir: Path, options: OpenProjectTask = OpenProjectTask()): Project?

  @Internal
  abstract fun loadProject(path: Path): Project

  @get:TestOnly
  abstract val isDefaultProjectInitialized: Boolean

  abstract fun isProjectOpened(project: Project): Boolean

  abstract fun canClose(project: Project): Boolean

  /**
   * The project and the app settings will be not saved.
   */
  @TestOnly
  fun forceCloseProject(project: Project): Boolean = forceCloseProject(project, save = false)

  @TestOnly
  abstract fun forceCloseProject(project: Project, save: Boolean): Boolean

  @Internal
  @TestOnly
  abstract suspend fun forceCloseProjectAsync(project: Project, save: Boolean = false): Boolean

  @Internal
  fun saveAndForceCloseProject(project: Project): Boolean = forceCloseProject(project, save = true)

  // return true if successful
  abstract fun closeAndDisposeAllProjects(checkCanClose: Boolean): Boolean

  @get:Internal
  abstract val allExcludedUrls: List<String>
}