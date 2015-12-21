/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.ide.actions

import com.intellij.diagnostic.VMOptions
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

abstract class EditCustomSettingsAction : DumbAwareAction() {
  protected abstract fun file(): File?
  protected abstract fun template(): String

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null && file() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val file = file() ?: return

    if (!file.exists()) {
      val message = IdeBundle.message("edit.custom.settings.confirm", FileUtil.getLocationRelativeToUserHome(file.path))
      val result = Messages.showYesNoDialog(project, message, e.presentation.text!!, Messages.getQuestionIcon())
      if (result == Messages.NO) return
      FileUtil.writeToFile(file, template())
    }

    val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
    if (vFile != null) {
      vFile.refresh(false, false)
      OpenFileDescriptor(project, vFile, vFile.length.toInt()).navigate(true)
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
    val file = lazy {
      val dir = PathManager.getCustomOptionsDirectory()
      return@lazy if (dir != null) File(dir, VMOptions.getCustomFileName()) else null
    }
  }

  override fun file(): File? = EditCustomVmOptionsAction.file.value
  override fun template(): String = "# custom ${ApplicationNamesInfo.getInstance().fullProductName} VM options\n\n${VMOptions.read() ?: ""}"

  class AccessExtension : NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile): Boolean = FileUtil.pathsEqual(file.path, EditCustomVmOptionsAction.file.value?.path)
  }
}