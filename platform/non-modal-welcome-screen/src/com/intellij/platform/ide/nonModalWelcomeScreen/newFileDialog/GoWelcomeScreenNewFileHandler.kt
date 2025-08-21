package com.intellij.platform.ide.nonModalWelcomeScreen.newFileDialog

import com.goide.i18n.GoBundle
import com.intellij.platform.ide.nonModalWelcomeScreen.leftPanel.GoWelcomeScreenFileTemplateOptionProvider
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
import java.nio.file.Path

object GoWelcomeScreenNewFileHandler {

  private object TemplateNames {
    const val GENERIC_EMPTY_FILE: String = "Generic Empty File"
    const val HTTP_REQUEST: String = "HTTP Request.http"
    const val DOCKERFILE: String = "Dockerfile"
  }

  fun getDefaultProjectPath(): String {
    return findSequentNonexistentFile(Path.of(getBaseDir()).toFile(), "awesomeProject", "").absolutePath
  }

  fun createEmptyFile(project: Project?) {
    if (project == null) return
    val dialogBuilder = GoWelcomeScreenNewFileDialog.Builder(project, GoBundle.message("go.non.modal.welcome.screen.create.file.dialog.title.file"))
    dialogBuilder.apply {
      defaultDirectory = getDefaultProjectPath()
    }

    showDialogAndCreateFile(project, dialogBuilder.build()) {
      TemplateNames.GENERIC_EMPTY_FILE
    }
  }

  fun createHttpRequestFile(project: Project?) {
    if (project == null) return
    val dialogBuilder = GoWelcomeScreenNewFileDialog.Builder(project, GoBundle.message("go.non.modal.welcome.screen.create.file.dialog.title.http.request"))
    dialogBuilder.apply {
      fixedExtension = "http"
      defaultDirectory = getDefaultProjectPath()
    }

    showDialogAndCreateFile(project, dialogBuilder.build()) {
      TemplateNames.HTTP_REQUEST
    }
  }

  fun createDockerfile(project: Project?) {
    if (project == null) return
    val dialogBuilder = GoWelcomeScreenNewFileDialog.Builder(project, GoBundle.message("go.non.modal.welcome.screen.create.file.dialog.title.dockerfile"))
    dialogBuilder.apply {
      defaultFileName = "Dockerfile"
      defaultDirectory = getDefaultProjectPath()
    }

    showDialogAndCreateFile(project, dialogBuilder.build()) {
      TemplateNames.DOCKERFILE
    }
  }

  private const val KUBERNETES_RESOURCE_TEMPLATE_KEY = "kubernetes.resource.template"

  fun createKubernetesResource(project: Project?) {
    if (project == null) return
    val dialogBuilder = GoWelcomeScreenNewFileDialog.Builder(project, GoBundle.message("go.non.modal.welcome.screen.create.file.dialog.title.k8s.resource"))
    dialogBuilder.apply {
      fixedExtension = "yaml"
      defaultDirectory = getDefaultProjectPath()
      templateOptions = GoWelcomeScreenFileTemplateOptionProvider.getForTemplateKey(KUBERNETES_RESOURCE_TEMPLATE_KEY)?.getTemplateOptions()
                        ?: emptyList()
    }
    showDialogAndCreateFile(project, dialogBuilder.build()) { it }
  }

  private fun showDialogAndCreateFile(
    project: Project,
    dialog: GoWelcomeScreenNewFileDialog,
    templateNameProvider: (String?) -> String?,
  ) {
    if (!dialog.showAndGet()) return

    val directory = dialog.getTargetDirectory() ?: return
    val templateName = templateNameProvider(dialog.getSelectedTemplateName()) ?: return
    val fileName = dialog.getFileName()

    try {
      val mkdirs = CreateFileAction.MkDirs(fileName, directory)
      val template = FileTemplateManager.getInstance(project).getInternalTemplate(templateName)
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
      showErrorMessage(project, GoBundle.message("go.non.modal.welcome.screen.error.dialog.message.cannot.create.file", fileName, e.message ?: "Unknown error"))
    }
  }

  private fun showErrorMessage(project: Project, @NlsContexts.DialogMessage message: String) {
    ApplicationManager.getApplication().invokeLater{
      Messages.showMessageDialog(project, message, GoBundle.message("go.non.modal.welcome.screen.error.dialog.title.cannot.create.file"), Messages.getErrorIcon())
    }
  }
}