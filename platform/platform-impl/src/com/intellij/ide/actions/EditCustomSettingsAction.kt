// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.CommonBundle
import com.intellij.diagnostic.VMOptions
import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.psi.PsiManager
import com.intellij.ui.EditorTextField
import com.intellij.util.ui.IoErrorText
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import javax.swing.JFrame
import javax.swing.ScrollPaneConstants

abstract class EditCustomSettingsAction : DumbAwareAction() {
  protected abstract fun file(): Path?
  protected abstract fun template(): String

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = (e.project != null || WelcomeFrame.getInstance() != null) && file() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val file = file() ?: return

    val project = e.project
    if (project != null) {
      openInEditor(file, project)
    }
    else {
      val frame = WelcomeFrame.getInstance() as JFrame?
      if (frame != null) {
        openInDialog(file, frame)
      }
    }
  }

  private fun openInEditor(file: Path, project: Project) {
    if (!Files.exists(file)) {
      try {
        Files.write(file, template().split('\n'))
      }
      catch (e: IOException) {
        Logger.getInstance(javaClass).warn(file.toString(), e)
        val message = IdeBundle.message("file.write.error.details", file, IoErrorText.message(e))
        Messages.showErrorDialog(project, message, CommonBundle.getErrorTitle())
        return
      }
    }

    val vFile = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(file)
    if (vFile != null) {
      vFile.refresh(false, false)
      val psiFile = PsiManager.getInstance(project).findFile(vFile)
      if (psiFile != null) {
        PsiNavigationSupport.getInstance().createNavigatable(project, vFile, psiFile.textLength).navigate(true)
      }
    }
  }

  private fun openInDialog(file: Path, frame: JFrame) {
    val text = if (!Files.exists(file)) template() else {
      try {
        Files.readAllLines(file).joinToString("\n")
      }
      catch (e: IOException) {
        Logger.getInstance(javaClass).warn(file.toString(), e)
        val message = IdeBundle.message("file.read.error.details", file, IoErrorText.message(e))
        Messages.showErrorDialog(frame, message, CommonBundle.getErrorTitle())
        return
      }
    }

    object : DialogWrapper(frame, true) {
      private val editor: EditorTextField

      init {
        title = FileUtil.getLocationRelativeToUserHome(file.toString())
        setOKButtonText(IdeBundle.message("button.save"))

        val document = EditorFactory.getInstance().createDocument(text)
        val defaultProject = DefaultProjectFactory.getInstance().defaultProject
        val fileType = FileTypeManager.getInstance().getFileTypeByFileName(file.fileName.toString())
        editor = object : EditorTextField(document, defaultProject, fileType, false, false) {
          override fun createEditor(): EditorEx {
            val editor = super.createEditor()
            editor.scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED
            editor.scrollPane.horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED
            return editor
          }
        }

        init()
      }

      override fun createCenterPanel() = editor
      override fun getPreferredFocusedComponent() = editor
      override fun getDimensionServiceKey() = "ide.config.custom.settings"

      override fun doOKAction() {
        try {
          Files.write(file, editor.text.split('\n'))
          close(OK_EXIT_CODE)
        }
        catch (e: IOException) {
          Logger.getInstance(javaClass).warn(file.toString(), e)
          val message = IdeBundle.message("file.write.error.details", file, IoErrorText.message(e))
          Messages.showErrorDialog(this.window, message, CommonBundle.getErrorTitle())
        }
      }
    }.show()
  }
}

class EditCustomPropertiesAction : EditCustomSettingsAction() {
  private companion object {
    val file: Lazy<Path?> = lazy { PathManager.getCustomOptionsDirectory()?.let { Path.of(it, PathManager.PROPERTIES_FILE_NAME) } }
  }

  override fun file(): Path? = file.value
  override fun template(): String = "# custom ${ApplicationNamesInfo.getInstance().fullProductName} properties\n\n"

  class AccessExtension : NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile): Boolean =
      EditCustomPropertiesAction.file.value?.let { VfsUtilCore.pathEqualsTo(file, it.toString()) } ?: false
  }
}

class EditCustomVmOptionsAction : EditCustomSettingsAction() {
  private companion object {
    val file: Lazy<Path?> = lazy { VMOptions.getWriteFile() }
  }

  override fun file(): Path? = file.value
  override fun template(): String = "# custom ${ApplicationNamesInfo.getInstance().fullProductName} VM options\n\n${VMOptions.read() ?: ""}"

  fun isEnabled(): Boolean = file() != null

  class AccessExtension : NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile): Boolean =
      EditCustomVmOptionsAction.file.value?.let { VfsUtilCore.pathEqualsTo(file, it.toString()) } ?: false
  }
}
