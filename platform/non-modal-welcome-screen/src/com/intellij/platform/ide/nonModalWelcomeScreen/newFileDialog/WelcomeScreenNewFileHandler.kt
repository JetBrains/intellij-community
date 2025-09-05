package com.intellij.platform.ide.nonModalWelcomeScreen.newFileDialog

import com.intellij.ide.actions.CreateFileAction
import com.intellij.ide.fileTemplates.FileTemplateManager
import com.intellij.ide.fileTemplates.FileTemplateUtil
import com.intellij.ide.fileTemplates.actions.CreateFromTemplateManager
import com.intellij.ide.impl.ProjectUtil.getBaseDir
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.io.FileUtil.findSequentNonexistentFile
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.ide.nonModalWelcomeScreen.NonModalWelcomeScreenBundle
import org.jetbrains.annotations.ApiStatus
import java.nio.file.Path

@ApiStatus.Internal
@ApiStatus.Experimental
object WelcomeScreenNewFileHandler {
  private fun getDefaultProjectPath(): String {
    val filePrefix = WelcomeScreenProjectProvider.getCreateNewFileProjectPrefix()
    return findSequentNonexistentFile(Path.of(getBaseDir()).toFile(), filePrefix, "").absolutePath
  }

  fun showNewFileDialog(project: Project,
                        @NlsContexts.DialogTitle dialogTitle: String,
                        templateName: String,
                        dialogBuilderBlock: WelcomeScreenNewFileDialog.Builder.() -> Unit = {}) {
    val dialogBuilder = WelcomeScreenNewFileDialog.Builder(project, dialogTitle)
    dialogBuilder.defaultDirectory = getDefaultProjectPath()
    dialogBuilder.dialogBuilderBlock()
    showDialogAndCreateFile(dialogBuilder.build(), templateName)
  }

  private fun showDialogAndCreateFile(
    dialog: WelcomeScreenNewFileDialog,
    templateName: String,
  ) {
    if (!dialog.showAndGet()) return

    val directory = dialog.getTargetDirectory() ?: return
    val fileName = dialog.getFileName()

    val project = dialog.project

    try {
      val mkdirs = CreateFileAction.MkDirs(fileName, directory)
      val selectedTemplateName = dialog.getSelectedTemplateName() ?: templateName
      val template = FileTemplateManager.getInstance(project).getInternalTemplate(selectedTemplateName)
      val templateProperties = FileTemplateManager.getInstance(project).getDefaultProperties()
      val psiFile = FileTemplateUtil.createFromTemplate(template, mkdirs.newName, templateProperties, mkdirs.directory).getContainingFile()
      val virtualFile = psiFile.getVirtualFile()
      if (virtualFile != null) {
        NonProjectFileWritingAccessProvider.allowWriting(listOf(virtualFile))
        ApplicationManager.getApplication().invokeLater {
          if (template.isLiveTemplateEnabled()) {
            CreateFromTemplateManager.startLiveTemplate(psiFile, emptyMap())
          }
          else {
            FileEditorManager.getInstance(project).openFile(virtualFile, true)
          }
        }
      }
    }
    catch (e: Exception) {
      showErrorMessage(project, NonModalWelcomeScreenBundle.message("welcome.screen.error.dialog.message.cannot.create.file", fileName, e.message ?: "Unknown error"))
    }
  }

  private fun showErrorMessage(project: Project, @NlsContexts.DialogMessage message: String) {
    ApplicationManager.getApplication().invokeLater{
      Messages.showMessageDialog(project, message, NonModalWelcomeScreenBundle.message("welcome.screen.error.dialog.title.cannot.create.file"), Messages.getErrorIcon())
    }
  }
}
