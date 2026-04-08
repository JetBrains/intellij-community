// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.filters

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.ApiStatus.Internal

abstract class FileHyperlinkInfoBase
@JvmOverloads
constructor(
  private val project: Project,
  private val documentLine: Int,
  private val documentColumn: Int,
  private val useBrowser: Boolean = true,
) : FileHyperlinkInfo {
  protected abstract val virtualFile: VirtualFile?

  override fun getDescriptor(): OpenFileDescriptor? {
    val file = virtualFile
    if (file == null || !file.isValid) {
      return null
    }

    val document = ProjectLocator.withPreferredProject(file, project).use {
      // need to load decompiler text
      FileDocumentManager.getInstance().getDocument(file)
    }
    val line = file.getUserData(LineNumbersMapping.LINE_NUMBERS_MAPPING_KEY)?.let { mapping ->
      val mappingLine = mapping.bytecodeToSource(documentLine + 1) - 1
      if (mappingLine < 0) null else mappingLine
    } ?: documentLine

    val offset = document?.let { calculateOffset(document = it, documentLine = line, documentColumn = documentColumn) }
    if (offset == null) {
      // although document position != logical position, it seems better than returning 'null'
      return OpenFileDescriptor(project, file, line, documentColumn)
    }
    else {
      return OpenFileDescriptor(project, file, offset)
    }
  }

  override fun navigate(project: Project) {
    navigateFileHyperlinkLegacy(project = project, descriptor = this.descriptor ?: return, useBrowser = useBrowser)
  }

  @get:Internal
  val isUseBrowserForNavigation: Boolean
    get() = useBrowser

  /**
   * Calculates an offset that matches the given line and column of the document.
   *
   * @param document [Document] instance
   * @param documentLine zero-based line of the document
   * @param documentColumn zero-based column of the document
   * @return calculated offset or `null` if it's impossible to calculate
   */
  protected open fun calculateOffset(document: Document, documentLine: Int, documentColumn: Int): Int? {
    if (documentLine < 0 || document.lineCount <= documentLine) {
      return null
    }

    val lineStartOffset = document.getLineStartOffset(documentLine)
    val lineEndOffset = document.getLineEndOffset(documentLine)
    val fixedColumn = documentColumn.coerceAtLeast(0).coerceAtMost(lineEndOffset - lineStartOffset)
    return lineStartOffset + fixedColumn
  }
}