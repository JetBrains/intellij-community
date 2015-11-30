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

import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.impl.NonProjectFileWritingAccessExtension
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class EditCustomPropertiesAction : AnAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null && PathManager.getCustomOptionsDirectory() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val dir = PathManager.getCustomOptionsDirectory() ?: return

    val file = File(dir, PathManager.PROPERTIES_FILE_NAME)
    if (!file.exists()) {
      val message = IdeBundle.message("edit.custom.properties.confirm", file.path)
      val result = Messages.showYesNoDialog(project, message, IdeBundle.message("edit.custom.properties.title"), Messages.getQuestionIcon())
      if (result == Messages.NO) return
      FileUtil.writeToFile(file, "# custom ${ApplicationNamesInfo.getInstance().fullProductName} properties\n\n")
    }

    val vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
    if (vFile != null) {
      vFile.refresh(false, false)
      OpenFileDescriptor(project, vFile, vFile.length.toInt()).navigate(true)
    }
  }

  class AccessExtension : NonProjectFileWritingAccessExtension {
    override fun isWritable(file: VirtualFile): Boolean =
        FileUtil.pathsEqual(file.path, "${PathManager.getCustomOptionsDirectory()}/${PathManager.PROPERTIES_FILE_NAME}")
  }
}