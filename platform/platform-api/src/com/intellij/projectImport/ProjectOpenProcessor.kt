// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.projectImport

import com.intellij.ide.IdeCoreBundle
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.MessageConstants
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.yesNoCancel
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.Nls
import java.util.*
import javax.swing.Icon

abstract class ProjectOpenProcessor {
  companion object {
    @JvmField
    val EXTENSION_POINT_NAME = ExtensionPointName<ProjectOpenProcessor>("com.intellij.projectOpenProcessor")

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
  }

  abstract val name: @Nls String

  /**
   * @return true if this open processor should be ranked over general .idea and .ipr files even if those exist.
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

  /**
   * Create an instance of the project, configure the project according to the needs of this ProjectOpenProcessor, and open it.
   *
   * If this processor calls some potentially untrusted code, then the processor should show a confirmation warning to the user,
   * allowing to load the project in some sort of "preview mode", where the user will be able to view the code, but nothing dangerous
   * will be executed automatically. See TrustedProjects#confirmOpeningUntrustedProject().
   *
   * @return The created project, or null if it was not possible to create a project for some reason.
   */
  abstract fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project?

  /**
   * Return not null Optional if supported.
   */
  open suspend fun openProjectAsync(virtualFile: VirtualFile,
                                    projectToClose: Project?,
                                    forceOpenInNewFrame: Boolean): Optional<Project>? {
    return null
  }

  /**
   * Allow opening a directory directly if the project files are located in that directory.
   *
   * @return true if project files are searched inside the selected directory, false if the project files must be selected directly.
   */
  open fun lookForProjectsInDirectory(): Boolean = true

  /**
   * Returns true if this processor is able to import the project after it has been opened in IDEA.
   *
   * @see .importProjectAfterwards
   */
  open fun canImportProjectAfterwards(): Boolean = false

  /**
   * Import the project after it has already been opened in IDEA.
   *
   * @see .canImportProjectAfterwards
   */
  open fun importProjectAfterwards(project: Project, file: VirtualFile) {}
}