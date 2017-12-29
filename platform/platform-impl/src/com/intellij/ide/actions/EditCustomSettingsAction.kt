/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.ide.actions

import com.intellij.CommonBundle
import com.intellij.diagnostic.VMOptions
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.DefaultProjectFactory
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame
import com.intellij.ui.EditorTextField
import com.intellij.util.LineSeparator
import java.io.File
import java.io.IOException
import javax.swing.JFrame
import javax.swing.ScrollPaneConstants

abstract class EditCustomSettingsAction : DumbAwareAction() {
  protected abstract fun file(): File?
  protected abstract fun template(): String

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = (e.project != null || WelcomeFrame.getInstance() != null) && file() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project
    val frame = WelcomeFrame.getInstance() as JFrame?
    val file = file() ?: return

    if (project != null) {
      if (!file.exists()) {
        val confirmation = IdeBundle.message("edit.custom.settings.confirm", FileUtil.getLocationRelativeToUserHome(file.path))
        val title = e.presentation.text!!
        val ok = IdeBundle.message("button.create")
        val cancel = IdeBundle.message("button.cancel")
        val result = Messages.showOkCancelDialog(project, confirmation, title, ok, cancel, Messages.getQuestionIcon())
        if (result == Messages.CANCEL) return

        try {
          FileUtil.writeToFile(file, template())
        }
        catch (ex: IOException) {
          Logger.getInstance(javaClass).warn(file.path, ex)
          val message = IdeBundle.message("edit.custom.settings.failed", file, ex.message)
          Messages.showErrorDialog(project, message, CommonBundle.message("title.error"))
          return
        }
      }

      val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
      if (vFile != null) {
        vFile.refresh(false, false)
        OpenFileDescriptor(project, vFile, vFile.length.toInt()).navigate(true)
      }
    }
    else if (frame != null) {
      val text = if (file.exists()) StringUtil.convertLineSeparators(FileUtil.loadFile(file)) else template()

      object : DialogWrapper(frame, true) {
        private val editor: EditorTextField

        init {
          title = FileUtil.getLocationRelativeToUserHome(file.path)
          setOKButtonText(IdeBundle.message("button.save"))

          val document = EditorFactory.getInstance().createDocument(text)
          val defaultProject = DefaultProjectFactory.getInstance().defaultProject
          val fileType = FileTypeManager.getInstance().getFileTypeByFileName(file.name)
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
          val toSave = StringUtil.convertLineSeparators(editor.text, LineSeparator.getSystemLineSeparator().separatorString)
          try {
            FileUtil.writeToFile(file, toSave)
            close(OK_EXIT_CODE)
          }
          catch (ex: IOException) {
            Logger.getInstance(javaClass).warn(file.path, ex)
            val message = IdeBundle.message("edit.custom.settings.failed", file, ex.message)
            Messages.showErrorDialog(this.window, message, CommonBundle.message("title.error"))
          }
        }
      }.show()
    }
  }
}

class EditCustomPropertiesAction : EditCustomSettingsAction() {
  private companion object {
    val file = lazy {
      val dir = PathManager.getCustomOptionsDirectory()
      return@lazy if (dir != null) File(dir, PathManager.PROPERTIES_FILE_NAME) else null
    }
  }

  override fun file(): File? = EditCustomPropertiesAction.file.value
  override fun template(): String = "# custom ${ApplicationNamesInfo.getInstance().fullProductName} properties\n\n"

  class AccessExtension : NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile): Boolean = FileUtil.pathsEqual(file.path, EditCustomPropertiesAction.file.value?.path)
  }
}

class EditCustomVmOptionsAction : EditCustomSettingsAction() {
  private companion object {
    val file = lazy { VMOptions.getWriteFile() }
  }

  override fun file(): File? = EditCustomVmOptionsAction.file.value
  override fun template(): String = "# custom ${ApplicationNamesInfo.getInstance().fullProductName} VM options\n\n${VMOptions.read() ?: ""}"

  fun isEnabled() = file() != null

  class AccessExtension : NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile): Boolean = FileUtil.pathsEqual(file.path, EditCustomVmOptionsAction.file.value?.path)
  }
}