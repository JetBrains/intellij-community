// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectImport

import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.application.EDT
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageConstants
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNoCancel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls
import javax.swing.Icon

abstract class ProjectOpenProcessor {
  /**
   * Describes the user's original intent when opening a project.
   *
   * Plugins must not implement this interface.
   *
   * Plugins should convert instances of this interface to `com.intellij.ide.impl.OpenProjectTask` with extension function
   * `ProjectOpenOptions.toOpenProjectTask()` (`com.intellij.ide.impl.ProjectUtilKt.toOpenProjectTask`) and use `OpenProjectTask` with
   * `com.intellij.openapi.project.ex.ProjectManagerEx.openProjectAsync` to open projects.
   *
   * `ProjectOpenOptions` is not modifiable - the platform captures user's intent, and this intent can only be analyzed, not changed.
   * On the other hand, plugins can modify the `OpenProjectTask` derived from `ProjectOpenOptions` if needed.
   */
  @ApiStatus.NonExtendable
  interface ProjectOpenOptions {
    /**
     * Whether to open the project in a new frame.
     */
    val forceOpenInNewFrame: Boolean

    /**
     * A project to close before opening the new one, if any.
     */
    val projectToClose: Project?
  }

  companion object {
    @JvmField
    val EXTENSION_POINT_NAME: ExtensionPointName<ProjectOpenProcessor> = ExtensionPointName("com.intellij.projectOpenProcessor")

    @JvmStatic
    fun getImportProvider(file: VirtualFile): ProjectOpenProcessor? = getImportProvider(file = file, onlyIfExistingProjectFile = false)

    /**
     * @param onlyIfExistingProjectFile when true, doesn't return 'generic' providers that can open any non-project directory/text file
     * (e.g. PlatformProjectOpenProcessor)
     */
    fun getImportProvider(file: VirtualFile, onlyIfExistingProjectFile: Boolean): ProjectOpenProcessor? {
      return EXTENSION_POINT_NAME.findFirstSafe { provider ->
        provider.canOpenProject(file) && (!onlyIfExistingProjectFile || provider.isProjectFile(file))
      }
    }

    @Internal
    val unimplementedOpenAsync: UnsupportedOperationException = UnsupportedOperationException()
  }

  abstract val name: @Nls String

  /**
   * @return true, if this open processor should be ranked over general .idea and .ipr files even if those exist.
   */
  open val isStrongProjectInfoHolder: Boolean
    get() = false

  open val icon: Icon?
    get() = null

  open fun getIcon(file: VirtualFile): Icon? = icon

  abstract fun canOpenProject(file: VirtualFile): Boolean

  open fun isProjectFile(file: VirtualFile): Boolean = canOpenProject(file)

  /**
   * If known that a user tries to open some project, ask if the user wants to open it as a plain file or as a project.
   * @return Messages.YES -> Open as a project, Messages.NO -> Open as a plain file, Messages.CANCEL -> Don't open.
   */
  @MessageConstants.YesNoCancelResult
  open fun askConfirmationForOpeningProject(file: VirtualFile, project: Project?): Int {
    return yesNoCancel(IdeCoreBundle.message("title.open.project"), IdeCoreBundle.message("message.open.file.is.project", file.name))
      .yesText(IdeCoreBundle.message("message.open.file.is.project.open.as.project"))
      .noText(IdeCoreBundle.message("message.open.file.is.project.open.as.file"))
      .cancelText(IdeCoreBundle.message("button.cancel"))
      .icon(UIUtil.getQuestionIcon())
      .show(project)
  }


  @ApiStatus.ScheduledForRemoval
  @Deprecated("Use openProjectAsync(VirtualFile, ProjectOpenOptions) instead", replaceWith = ReplaceWith("openProjectAsync(virtualFile, projectOpenOptions)"), level = DeprecationLevel.ERROR)
  open fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    error("Use `openProjectAsync` instead")
  }

  /**
   * Warning: This function *must* be implemented.
   *
   * Create an instance of the project, configure the project according to the needs of this ProjectOpenProcessor, and open it.
   *
   * If this processor calls some potentially untrusted code, then the processor should show a confirmation warning to the user,
   *  allowing us to load the project in some sort of "preview mode", where the user will be able to view the code, but nothing dangerous
   * will be executed automatically. See TrustedProjects#confirmOpeningUntrustedProject().
   *
   * @return The created project, or null if it was not possible to create a project for some reason.
   */
  open suspend fun openProjectAsync(
    virtualFile: VirtualFile,
    projectOpenOptions: ProjectOpenOptions,
  ): Project? {
    return openProjectAsync(virtualFile, projectOpenOptions.projectToClose, projectOpenOptions.forceOpenInNewFrame)
  }

  @Deprecated("Use openProjectAsync(VirtualFile, ProjectOpenOptions) instead", replaceWith = ReplaceWith("openProjectAsync(virtualFile, projectOpenOptions)"))
  open suspend fun openProjectAsync(
    virtualFile: VirtualFile,
    projectToClose: Project?,
    forceOpenInNewFrame: Boolean,
  ): Project? {
    throw unimplementedOpenAsync
  }

  /**
   * Allow opening a directory directly if the project files are located in that directory.
   *
   * @return true, if project files are searched inside the selected directory, false if the project files must be selected directly.
   */
  open fun lookForProjectsInDirectory(): Boolean = true

  /**
   * Returns true if this processor is able to import the project after it has been opened in IDEA.
   *
   * @see importProjectAfterwards
   */
  open fun canImportProjectAfterwards(): Boolean = false

  /**
   * Import the project after it has already been opened in IDEA.
   *
   * @see canImportProjectAfterwards
   */
  @Deprecated("use async method instead")
  open fun importProjectAfterwards(project: Project, file: VirtualFile) {
    throw UnsupportedOperationException()
  }

  /**
   * Import the project after it has already been opened in IDEA.
   *
   * @see canImportProjectAfterwards
   */
  open suspend fun importProjectAfterwardsAsync(project: Project, file: VirtualFile) {
    withContext(Dispatchers.EDT) {
      importProjectAfterwards(project, file)
    }
  }

}