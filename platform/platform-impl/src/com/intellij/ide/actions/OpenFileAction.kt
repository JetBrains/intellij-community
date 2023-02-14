// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.GeneralLocalSettings
import com.intellij.ide.IdeBundle
import com.intellij.ide.highlighter.ProjectFileType
import com.intellij.ide.impl.OpenProjectTask
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.lightEdit.*
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.fileChooser.FileChooser
import com.intellij.openapi.fileChooser.FileChooserDescriptor
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.PathChooserDialog
import com.intellij.openapi.fileChooser.impl.FileChooserUtil
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.fileTypes.ex.FileTypeChooser
import com.intellij.openapi.progress.ModalTaskOwner
import com.intellij.openapi.progress.runBlockingModalWithRawProgressReporter
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame
import com.intellij.openapi.wm.impl.welcomeScreen.NewWelcomeScreen
import com.intellij.platform.PlatformProjectOpenProcessor
import com.intellij.projectImport.ProjectOpenProcessor.Companion.getImportProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files

open class OpenFileAction : AnAction(), DumbAware, LightEditCompatible {
  companion object {
    @JvmStatic
    fun openFile(filePath: String, project: Project) {
      val file = LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
      if (file != null && file.isValid) {
        openFile(file, project)
      }
    }

    @JvmStatic
    fun openFile(file: VirtualFile, project: Project) {
      NonProjectFileWritingAccessProvider.allowWriting(listOf(file))
      if (LightEdit.owns(project)) {
        LightEditService.getInstance().openFile(file)
        LightEditFeatureUsagesUtil.logFileOpen(project, LightEditFeatureUsagesUtil.OpenPlace.LightEditOpenAction)
      }
      else {
        PsiNavigationSupport.getInstance().createNavigatable(project, file, -1).navigate(true)
      }
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val showFiles = project != null || PlatformProjectOpenProcessor.getInstanceIfItExists() != null
    val descriptor: FileChooserDescriptor = if (showFiles) ProjectOrFileChooserDescriptor() else ProjectOnlyFileChooserDescriptor()
    var toSelect: VirtualFile? = null
    val defaultProjectDirectory = GeneralLocalSettings.getInstance().defaultProjectDirectory
    if (defaultProjectDirectory.isNotEmpty()) {
      toSelect = VfsUtil.findFileByIoFile(File(defaultProjectDirectory), true)
    }
    descriptor.putUserData(PathChooserDialog.PREFER_LAST_OVER_EXPLICIT, toSelect == null && showFiles)
    FileChooser.chooseFiles(descriptor, project, toSelect ?: pathToSelect) { files ->
      for (file in files) {
        if (!descriptor.isFileSelectable(file)) {
          val message = IdeBundle.message("error.dir.contains.no.project", file.presentableUrl)
          Messages.showInfoMessage(project, message, IdeBundle.message("title.cannot.open.project"))
          return@chooseFiles
        }
      }
      @Suppress("DialogTitleCapitalization")
      runBlockingModalWithRawProgressReporter(owner = if (project == null) ModalTaskOwner.guess() else ModalTaskOwner.project(project),
                                              title = IdeBundle.message("title.open.project")) {
        for (file in files) {
          doOpenFile(project, file)
        }
      }
    }
  }

  @Suppress("unused")
  private class OnWelcomeScreen : OpenFileAction() {
    override fun update(e: AnActionEvent) {
      val presentation = e.presentation
      if (!NewWelcomeScreen.isNewWelcomeScreen(e)) {
        presentation.isEnabledAndVisible = false
        return
      }

      if (FlatWelcomeFrame.USE_TABBED_WELCOME_SCREEN) {
        presentation.icon = AllIcons.Welcome.Open
        presentation.selectedIcon = AllIcons.Welcome.OpenSelected
        presentation.text = ActionsBundle.message("action.Tabbed.WelcomeScreen.OpenProject.text")
      }
      else {
        presentation.icon = AllIcons.Actions.MenuOpen
      }
    }
  }

  protected val pathToSelect: VirtualFile?
    get() = VfsUtil.getUserHomeDir()

  override fun update(e: AnActionEvent) {
    if (NewWelcomeScreen.isNewWelcomeScreen(e)) {
      e.presentation.icon = AllIcons.Actions.MenuOpen
    }
  }

  override fun getActionUpdateThread() = ActionUpdateThread.BGT
}

private class ProjectOnlyFileChooserDescriptor : OpenProjectFileChooserDescriptor(true) {
  init {
    title = IdeBundle.message("title.open.project")
  }
}

// vanilla OpenProjectFileChooserDescriptor only accepts project files; this one is overridden to accept any files
private class ProjectOrFileChooserDescriptor : OpenProjectFileChooserDescriptor(true) {
  private val myStandardDescriptor = FileChooserDescriptorFactory.createSingleFileNoJarsDescriptor().withHideIgnored(false)

  init {
    title = IdeBundle.message("title.open.file.or.project")
  }

  override fun isFileVisible(file: VirtualFile, showHiddenFiles: Boolean): Boolean {
    return if (file.isDirectory) super.isFileVisible(file, showHiddenFiles) else myStandardDescriptor.isFileVisible(file, showHiddenFiles)
  }

  override fun isFileSelectable(file: VirtualFile?): Boolean {
    if (file == null) {
      return false
    }
    return if (file.isDirectory) super.isFileSelectable(file) else myStandardDescriptor.isFileSelectable(file)
  }

  override fun isChooseMultiple() = true
}

private suspend fun doOpenFile(project: Project?, virtualFile: VirtualFile) {
  val file = virtualFile.toNioPath()
  if (Files.isDirectory(file)) {
    @Suppress("TestOnlyProblems")
    ProjectUtil.openExistingDir(file, project)
    return
  }

  // try to open as a project - unless the file is an .ipr of the current one
  if ((project == null || virtualFile != project.projectFile) && OpenProjectFileChooserDescriptor.isProjectFile(virtualFile)) {
    val answer = shouldOpenNewProject(project, virtualFile)
    if (answer == Messages.CANCEL) {
      return
    }
    else if (answer == Messages.YES) {
      val openedProject = ProjectUtil.openOrImportAsync(file, OpenProjectTask { projectToClose = project })
      openedProject?.let {
        FileChooserUtil.setLastOpenedFile(it, file)
      }
      return
    }
  }

  LightEditUtil.markUnknownFileTypeAsPlainTextIfNeeded(project, virtualFile)
  readAction {
    FileTypeChooser.getKnownFileTypeOrAssociate(virtualFile, project)
  } ?: return
  if (project == null || project.isDefault) {
    PlatformProjectOpenProcessor.createTempProjectAndOpenFileAsync(file, OpenProjectTask { projectToClose = project })
  }
  else {
    NonProjectFileWritingAccessProvider.allowWriting(listOf(virtualFile))
    if (LightEdit.owns(project)) {
      LightEditService.getInstance().openFile(virtualFile)
      LightEditFeatureUsagesUtil.logFileOpen(project, LightEditFeatureUsagesUtil.OpenPlace.LightEditOpenAction)
    }
    else {
      val navigatable = PsiNavigationSupport.getInstance().createNavigatable(project, virtualFile, -1)
      withContext(Dispatchers.EDT) {
        navigatable.navigate(true)
      }
    }
  }
}

@Messages.YesNoCancelResult
private suspend fun shouldOpenNewProject(project: Project?, file: VirtualFile): Int {
  if (file.fileType is ProjectFileType) {
    return Messages.YES
  }

  val provider = getImportProvider(file) ?: return Messages.CANCEL
  return withContext(Dispatchers.EDT) { provider.askConfirmationForOpeningProject(file, project) }
}