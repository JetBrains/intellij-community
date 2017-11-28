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

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

abstract class FileHyperlinkInfoBase(private val myProject: Project,
                                     private val myDocumentLine: Int,
                                     private val myDocumentColumn: Int) : FileHyperlinkInfo {

  protected abstract val virtualFile: VirtualFile?

  override fun getDescriptor(): OpenFileDescriptor? {
    val file = virtualFile
    if (file == null || !file.isValid) return null

    val document = FileDocumentManager.getInstance().getDocument(file) // need to load decompiler text
    val line = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY)?.let { mapping ->
      val mappingLine = mapping.bytecodeToSource(myDocumentLine + 1) - 1
      if (mappingLine < 0) null else mappingLine
    } ?: myDocumentLine

    val offset = calculateOffset(document, line, myDocumentColumn)
    if (offset == null) {
      // although document position != logical position, it seems better than returning 'null'
      return OpenFileDescriptor(myProject, file, line, myDocumentColumn)
    }
    else {
      return OpenFileDescriptor(myProject, file, offset)
    }
  }

  override fun navigate(project: Project?) {
    project ?: return
    descriptor?.let {
      FileEditorManager.getInstance(project).openTextEditor(it, true)
    }
  }

  /**
   * Calculates an offset, that matches given line and column of the document.
   *
   * @param document [Document] instance
   * @param documentLine zero-based line of the document
   * @param documentColumn zero-based column of the document
   * @return calculated offset or `null` if it's impossible to calculate
   */
  protected open fun calculateOffset(document: Document?, documentLine: Int, documentColumn: Int): Int? {
    document ?: return null
    if (documentLine < 0 || document.lineCount <= documentLine) return null
    val lineStartOffset = document.getLineStartOffset(documentLine)
    val lineEndOffset = document.getLineEndOffset(documentLine)
    val fixedColumn = Math.min(Math.max(documentColumn, 0), lineEndOffset - lineStartOffset)
    return lineStartOffset + fixedColumn
  }
}