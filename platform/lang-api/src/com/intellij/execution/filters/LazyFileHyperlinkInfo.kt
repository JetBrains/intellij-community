/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.execution.filters

import com.intellij.ide.IdeBundle
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

open class LazyFileHyperlinkInfo(project: Project, filePath: String, documentLine: Int, documentColumn: Int)
  : FileHyperlinkInfoBase(project, documentLine, documentColumn) {

  override val virtualFile: VirtualFile? by lazy {
    LocalFileSystem.getInstance().refreshAndFindFileByPath(filePath)
  }
}

class LazyAbsoluteFileHyperLinkInfo(val project: Project,
                                    private val locatorInErrorMsg: String,
                                    absoluteFilePath: String,
                                    documentLine: Int,
                                    documentColumn: Int)
  : LazyFileHyperlinkInfo(project, absoluteFilePath, documentLine, documentColumn) {
  override fun getDescriptor(): OpenFileDescriptor? {
    val descriptor = super.getDescriptor()
    if (descriptor == null) {
      Messages.showErrorDialog(project, "Cannot find file ${StringUtil.trimMiddle(locatorInErrorMsg, 150)}",
                               IdeBundle.message("title.cannot.open.file"))
    }
    return descriptor
  }
}