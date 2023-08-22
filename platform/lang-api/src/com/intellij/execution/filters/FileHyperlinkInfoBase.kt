// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.filters

import com.intellij.ide.util.PsiNavigationSupport
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectLocator
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import java.io.File

abstract class FileHyperlinkInfoBase
@JvmOverloads
constructor(private val myProject: Project,
            private val myDocumentLine: Int,
            private val myDocumentColumn: Int,
            private val myUseBrowser: Boolean = true) : FileHyperlinkInfo {

  protected abstract val virtualFile: VirtualFile?

  override fun getDescriptor(): OpenFileDescriptor? {
    val file = virtualFile
    if (file == null || !file.isValid) return null

    val document = ProjectLocator.withPreferredProject(file, myProject).use {
      // need to load decompiler text
      FileDocumentManager.getInstance().getDocument(file)
    }
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

  override fun navigate(project: Project) {
    val descriptor = descriptor ?: return
    if (descriptor.file.isDirectory) {
      val psiManager = PsiManager.getInstance(project)
      val psiDirectory = psiManager.findDirectory(descriptor.file)
      if (psiDirectory != null && psiManager.isInProject(psiDirectory)) {
        psiDirectory.navigate(true)
      }
      else {
        PsiNavigationSupport.getInstance().openDirectoryInSystemFileManager(File(descriptor.file.path))
      }
    }
    else {
      if (null == FileEditorManager.getInstance(project).openTextEditor(descriptor, true) && myUseBrowser) {
        BrowserHyperlinkInfo(descriptor.file.url).navigate(project)
      }
    }
  }

  /**
   * Calculates an offset that matches the given line and column of the document.
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
    val fixedColumn = documentColumn.coerceAtLeast(0).coerceAtMost(lineEndOffset - lineStartOffset)
    return lineStartOffset + fixedColumn
  }
}