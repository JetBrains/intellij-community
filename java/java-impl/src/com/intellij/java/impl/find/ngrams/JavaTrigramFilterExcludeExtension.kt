// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.java.impl.find.ngrams

import com.intellij.find.ngrams.TrigramIndexFilterExcludeExtension
import com.intellij.ide.highlighter.JavaFileType
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.impl.source.JavaFileElementType
import com.intellij.util.indexing.IndexedFile

internal class JavaTrigramFilterExcludeExtension: TrigramIndexFilterExcludeExtension {
  override fun getFileType(): FileType = JavaFileType.INSTANCE

  override fun shouldExclude(file: IndexedFile): Boolean {
    val project = file.project ?: return false
    val vFile = file.file
    return !JavaFileElementType.isInSourceContent(vFile) && ProjectFileIndex.getInstance(project).isInLibrarySource(vFile)
  }
}