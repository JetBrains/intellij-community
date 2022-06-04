// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.projectImport

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.JavaUiBundle
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.util.projectWizard.WizardContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.components.StorageScheme
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.ProjectJdkTable
import com.intellij.openapi.projectRoots.ex.JavaSdkUtil
import com.intellij.openapi.roots.CompilerProjectExtension
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ui.configuration.ModulesProvider
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.file.Files
import javax.swing.Icon

abstract class ProjectOpenProcessorBase<T : ProjectImportBuilder<*>> : ProjectOpenProcessor {
  companion object {
    @JvmStatic
    protected fun canOpenFile(file: VirtualFile, supported: Array<String>): Boolean {
      return supported.contains(file.name)
    }

    @JvmStatic
    fun getUrl(path: String): String {
      var resolvedPath: String
      try {
        resolvedPath = FileUtil.resolveShortWindowsName(path)
      }
      catch (ignored: IOException) {
        resolvedPath = path
      }
      return VfsUtilCore.pathToUrl(resolvedPath)
    }
  }

  private val myBuilder: T?

  @Deprecated("Override {@link #doGetBuilder()} and use {@code ProjectImportBuilder.EXTENSIONS_POINT_NAME.findExtensionOrFail(yourClass.class)}.")
  @ApiStatus.ScheduledForRemoval
  protected constructor(builder: T) {
    myBuilder = builder
  }

  protected constructor() {
    myBuilder = null
  }

  open val builder: T
    get() = doGetBuilder()

  protected open fun doGetBuilder(): T = myBuilder!!

  override fun getName(): String = builder.name

  override fun getIcon(): Icon? = builder.icon

  override fun canOpenProject(file: VirtualFile): Boolean {
    val supported = supportedExtensions
    if (file.isDirectory) {
      return getFileChildren(file).any { canOpenFile(it, supported) }
    }
    else {
      return canOpenFile(file, supported)
    }
  }

  protected open fun doQuickImport(file: VirtualFile, wizardContext: WizardContext): Boolean = false

  abstract val supportedExtensions: Array<String>

  override fun doOpenProject(virtualFile: VirtualFile, projectToClose: Project?, forceOpenInNewFrame: Boolean): Project? {
    try {
      val wizardContext = WizardContext(null, null)
      builder.isUpdate = false

      var resolvedVirtualFile = virtualFile
      if (virtualFile.isDirectory) {
        val supported = supportedExtensions
        for (file in getFileChildren(virtualFile)) {
          if (canOpenFile(file, supported)) {
            resolvedVirtualFile = file
            break
          }
        }
      }
      wizardContext.setProjectFileDirectory(resolvedVirtualFile.parent.toNioPath(), false)

      if (!doQuickImport(resolvedVirtualFile, wizardContext)) {
        return null
      }

      if (wizardContext.projectName == null) {
        if (wizardContext.projectStorageFormat == StorageScheme.DEFAULT) {
          wizardContext.projectName = JavaUiBundle.message("project.import.default.name", name) + ProjectFileType.DOT_DEFAULT_EXTENSION
        }
        else {
          wizardContext.projectName = JavaUiBundle.message("project.import.default.name.dotIdea", name)
        }
      }

      wizardContext.projectJdk = ProjectRootManager.getInstance(ProjectManager.getInstance().defaultProject).projectSdk
                                 ?: ProjectJdkTable.getInstance().findMostRecentSdkOfType(JavaSdk.getInstance())

      val dotIdeaFile = wizardContext.projectDirectory.resolve(Project.DIRECTORY_STORE_FOLDER)
      val projectFile = wizardContext.projectDirectory.resolve(wizardContext.projectName + ProjectFileType.DOT_DEFAULT_EXTENSION).normalize()
      var pathToOpen = if (wizardContext.projectStorageFormat == StorageScheme.DEFAULT) projectFile.toAbsolutePath() else dotIdeaFile.parent
      var shouldOpenExisting = false
      var importToProject = true
      if (Files.exists(projectFile) || Files.exists(dotIdeaFile)) {
        if (ApplicationManager.getApplication().isHeadlessEnvironment) {
          shouldOpenExisting = true
          importToProject = true
        }
        else {
          val existingName: String
          if (Files.exists(dotIdeaFile)) {
            existingName = "an existing project"
            pathToOpen = dotIdeaFile.parent
          }
          else {
            existingName = "'${projectFile.fileName}'"
            pathToOpen = projectFile
          }

          val result = Messages.showYesNoCancelDialog(
            projectToClose,
            JavaUiBundle.message("project.import.open.existing", existingName, projectFile.parent, virtualFile.name),
            IdeBundle.message("title.open.project"),
            JavaUiBundle.message("project.import.open.existing.openExisting"),
            JavaUiBundle.message("project.import.open.existing.reimport"),
            CommonBundle.getCancelButtonText(),
            Messages.getQuestionIcon())
          if (result == Messages.CANCEL) {
            return null
          }

          shouldOpenExisting = result == Messages.YES
          importToProject = !shouldOpenExisting
        }
      }

      var options = OpenProjectTask(projectToClose = projectToClose, forceOpenInNewFrame = forceOpenInNewFrame, projectName = wizardContext.projectName)
      if (!shouldOpenExisting) {
        options = options.copy(isNewProject = true)
      }

      if (importToProject) {
        options = options.copy(beforeOpen = { project -> importToProject(project, projectToClose, wizardContext) })
      }

      try {
        val project = ProjectManagerEx.getInstanceEx().openProject(pathToOpen, options)
        ProjectUtil.updateLastProjectLocation(pathToOpen)
        return project
      }
      catch (e: Exception) {
        logger<ProjectOpenProcessorBase<*>>().warn(e)
      }
    }
    finally {
      builder.cleanup()
    }

    return null
  }

  private fun importToProject(projectToOpen: Project, projectToClose: Project?, wizardContext: WizardContext): Boolean {
    return invokeAndWaitIfNeeded {
      if (!builder.validate(projectToClose, projectToOpen)) {
        return@invokeAndWaitIfNeeded false
      }

      ApplicationManager.getApplication().runWriteAction {
        wizardContext.projectJdk?.let {
          JavaSdkUtil.applyJdkToProject(projectToOpen, it)
        }

        val projectDirPath = wizardContext.projectFileDirectory
        val path = projectDirPath + if (projectDirPath.endsWith('/')) "classes" else "/classes"
        CompilerProjectExtension.getInstance(projectToOpen)?.let {
          it.compilerOutputUrl = getUrl(path)
        }
      }
      builder.commit(projectToOpen, null, ModulesProvider.EMPTY_MODULES_PROVIDER)
      true
    }
  }
}

private fun getFileChildren(file: VirtualFile) = file.children ?: VirtualFile.EMPTY_ARRAY
