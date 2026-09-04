// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.ProjectUtil
import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchFileActions
import com.intellij.ide.scratch.ScratchFileActions.ChangeLanguageAction
import com.intellij.ide.scratch.ScratchFileCreationHelper
import com.intellij.ide.util.DeleteHandler
import com.intellij.ide.welcomeScreen.WelcomeUtils
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx
import com.intellij.openapi.fileEditor.impl.EditorWindow
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.fileEditor.impl.tabActions.CloseTab
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectCloseHandler
import com.intellij.openapi.ui.MessageConstants.YesNoCancelResult
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFilePreCloseCheck
import com.intellij.openapi.vfs.findOrCreateFile
import com.intellij.platform.PROJECT_CLOSE_WITH_CONFIRMATION
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.util.Consumer
import com.intellij.util.SystemProperties
import com.intellij.util.containers.toArray
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

internal class WelcomeNewEmptyFileAction : DumbAwareAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    val project = e.project
    e.presentation.isEnabledAndVisible = project != null && WelcomeUtils.isWelcomeProject(project)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val context = ScratchFileCreationHelper.Context()
    context.defaultRootType = WelcomeFilesRootType.Util.instance
    context.filePrefix = "Untitled"
    context.fileExtension = "txt"
    ScratchFileActions.doCreateNewScratch(project, context, e.dataContext)
  }
}

internal class WelcomeChangeLanguageAction : ChangeLanguageAction() {
  override fun update(e: AnActionEvent) {
    val project = e.project
    if (project != null && !WelcomeUtils.isWelcomeProject(project)) {
      e.presentation.isEnabledAndVisible = false
    }
    else {
      super.update(e)
    }
  }

  override fun fileFilter(project: Project): Condition<VirtualFile> {
    return Condition { file -> !file.isDirectory() && WelcomeFilesRootType.Util.instance.containsFile(file) }
  }
}

internal class WelcomeFilesRootType : RootType("welcomeFiles", "") {
  object Util {
    val instance: WelcomeFilesRootType
      get() = findByClass(WelcomeFilesRootType::class.java)

    fun isWelcomeFile(project: Project, file: VirtualFile): Boolean {
      return WelcomeUtils.isWelcomeProject(project) && instance.containsFile(file)
    }
  }

  override fun allowOpenFileEventsForHidden(): Boolean = true

  override fun fileOpened(file: VirtualFile, source: FileEditorManager) {
    val fileEditorManager = source as FileEditorManagerEx
    for (fileEditor in source.getAllEditors(file)) {
      if (fileEditor is TextEditor) {
        for (window in fileEditorManager.windows) {
          val tabs = window.tabbedPane.tabs.tabs
          for (tab in tabs) {
            if (tab.`object` == file) {
              val actions = tab.tabLabelActions
              if (actions is DefaultActionGroup) {
                for (action in actions.getChildren(ActionManager.getInstance())) {
                  if (action is CloseTab) {
                    action.showModifier = true
                    return
                  }
                }
              }
              return
            }
          }
        }
      }
    }
  }
}

internal class WelcomeNonProjectFileWritingAccessExtension : NonProjectFileWritingAccessExtension {
  override fun isWritable(file: VirtualFile): Boolean {
    val project = ProjectUtil.getActiveProject()
    return project != null && WelcomeFilesRootType.Util.isWelcomeFile(project, file)
  }
}

internal class WelcomeFilePreCloseCheck : VirtualFilePreCloseCheck {
  override fun canCloseFile(file: VirtualFile): Boolean {
    val project = ProjectUtil.getActiveProject()
    return project == null || !WelcomeFilesRootType.Util.isWelcomeFile(project, file) || doCloseFile(project, file)
  }
}

@ApiStatus.Internal
class WelcomeSaveFileAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    val project = event.project
    event.presentation.isEnabledAndVisible = project != null && getFile(project, event) != null
    event.presentation.text = ActionsBundle.message("action.WelcomeSaveFileAction.text")
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    val file = getFile(project, event) ?: return
    doSaveFile(project, file, true)
  }

  private fun getFile(project: Project, event: AnActionEvent): VirtualFile? {
    if (!WelcomeUtils.isWelcomeProject(project)) {
      return null
    }
    val document = event.getData(CommonDataKeys.EDITOR)?.document
    val file = if (document == null) {
      event.getData(EditorWindow.DATA_KEY)?.getContextFile() ?: return null
    }
    else {
      FileDocumentManager.getInstance().getFile(document) ?: return null
    }
    if (!WelcomeFilesRootType.Util.instance.containsFile(file)) {
      return null
    }
    return file
  }
}

internal class WelcomeProjectCloseHandler : ProjectCloseHandler {
  override fun canClose(project: Project): Boolean {
    if (WelcomeUtils.isWelcomeProject(project) && project.getUserData(PROJECT_CLOSE_WITH_CONFIRMATION) == true) {
      val rootType = WelcomeFilesRootType.Util.instance
      val files = FileEditorManager.getInstance(project).openFiles.filter { rootType.containsFile(it) }
      val size = files.size

      if (size == 1) {
        return doCloseFileOnExit(project, files[0])
      }
      if (size > 1) {
        return doCloseFilesOnExit(project, files)
      }
    }
    return true
  }
}

private fun doCloseFile(project: Project, file: VirtualFile): Boolean {
  val result = askSaveFile(file, project)

  if (result == Messages.CANCEL) {
    return false
  }
  if (result == Messages.OK) {
    return doSaveFile(project, file, false)
  }

  ApplicationManager.getApplication().invokeLater({ deleteFile(project, file) }, project.disposed)
  return true
}

private fun doCloseFileOnExit(project: Project, file: VirtualFile): Boolean {
  val result = askSaveFile(file, project)

  if (result == Messages.CANCEL) {
    return false
  }
  if (result == Messages.OK) {
    return doSaveFileOnExit(project, file)
  }

  deleteFilesOnExit(project, listOf(file))
  return true
}

@YesNoCancelResult
private fun askSaveFile(file: VirtualFile, project: Project): Int {
  return MessageDialogBuilder.yesNoCancel(IdeBundle.message("welcome.file.dialog.title", file.name),
                                          IdeBundle.message("welcome.file.dialog.messages"))
    .yesText(IdeBundle.message("button.save")).noText(ApplicationBundle.message("settings.switch.project.button.dont.save")).show(project)
}

private fun doCloseFilesOnExit(project: Project, files: List<VirtualFile>): Boolean {
  val result = MessageDialogBuilder.yesNoCancel(IdeBundle.message("welcome.files.dialog.title"),
                                                IdeBundle.message("welcome.files.dialog.messages",
                                                                  files.size.toString(),
                                                                  files.joinToString("<br>") { it.name }))
    .yesText(IdeBundle.message("button.save")).noText(ApplicationBundle.message("settings.switch.project.button.dont.save")).show(project)

  if (result == Messages.CANCEL) {
    return false
  }
  if (result == Messages.OK) {
    return doSaveFilesOnExit(project, files)
  }

  deleteFilesOnExit(project, files)
  return true
}

private fun doSaveFile(project: Project, file: VirtualFile, closeCurrentTab: Boolean): Boolean {
  val targetFile = showSaveFileDialog(project, file) ?: return false

  ApplicationManager.getApplication().invokeLater(
    {
      ApplicationManager.getApplication().runWriteAction(Runnable {
        val manager = FileDocumentManager.getInstance()
        val source = manager.getDocument(file)!!
        val target = manager.getDocument(targetFile)!!

        targetFile.refresh(false, false)
        target.setText(source.charsSequence)
        manager.saveDocument(target)

        ApplicationManager.getApplication().invokeLater(
          {
            val fileEditorManager = FileEditorManager.getInstance(project)

            if (closeCurrentTab) {
              fileEditorManager.closeFile(file)
            }
            fileEditorManager.openFile(targetFile)

            deleteFile(project, file)
          }, project.disposed)
      })
    }, project.disposed)

  return true
}

private fun doSaveFileOnExit(project: Project, file: VirtualFile): Boolean {
  val targetFile = showSaveFileDialog(project, file) ?: return false

  ApplicationManager.getApplication().runWriteAction(Runnable {
    val manager = FileDocumentManager.getInstance()
    val source = manager.getDocument(file)!!
    val target = manager.getDocument(targetFile)!!

    targetFile.refresh(false, false)
    target.setText(source.charsSequence)
    manager.saveDocument(target)
  })

  deleteFilesOnExit(project, listOf(file))

  return true
}

private fun showSaveFileDialog(project: Project, file: VirtualFile): VirtualFile? {
  val descriptor = FileSaverDescriptor(IdeBundle.message("dialog.title.save.as"), IdeBundle.message("label.choose.target.file"))
  val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, project)

  val fileWrapper = dialog.save(Path.of(SystemProperties.getUserHome()), file.name)

  return fileWrapper?.getVirtualFile(true) // DOTO: slowOps
}

private fun doSaveFilesOnExit(project: Project, files: List<VirtualFile>): Boolean {
  var targetDirRef: VirtualFile? = null
  val dialog = FileChooserFactory.getInstance().createPathChooser(FileChooserDescriptorFactory.singleDir(), project, null)
  dialog.choose(VfsUtil.getUserHomeDir(), Consumer { targetDirRef = it.firstOrNull() })

  val targetDir = targetDirRef
  if (targetDir == null) {
    return false
  }

  ApplicationManager.getApplication().runWriteAction(Runnable {
    val manager = FileDocumentManager.getInstance()

    for (file in files) {
      val targetFile = targetDir.findOrCreateFile(file.name)

      val source = manager.getDocument(file)!!
      val target = manager.getDocument(targetFile)!!

      targetFile.refresh(false, false)
      target.setText(source.charsSequence)
      manager.saveDocument(target)
    }
  })

  deleteFilesOnExit(project, files)

  return true
}

private fun deleteFile(project: Project, file: VirtualFile) {
  if (file.isValid()) {
    val psiFile = PsiManager.getInstance(project).findFile(file)
    if (psiFile != null) {
      DeleteHandler.deletePsiElement(arrayOf(psiFile), project, false)
    }
  }
}

private fun deleteFilesOnExit(project: Project, files: List<VirtualFile>) {
  val psiManager = PsiManager.getInstance(project)
  val fileEditorManager = FileEditorManager.getInstance(project)
  val toDelete = mutableListOf<PsiElement>()

  for (file in files) {
    fileEditorManager.closeFile(file)

    if (file.isValid()) {
      val psiFile = psiManager.findFile(file)
      if (psiFile != null) {
        toDelete.add(psiFile)
      }
    }
  }

  DeleteHandler.deletePsiElement(toDelete.toArray(PsiElement.EMPTY_ARRAY), project, false)
}