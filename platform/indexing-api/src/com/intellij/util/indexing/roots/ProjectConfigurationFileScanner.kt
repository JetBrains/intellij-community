// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.ProjectScope
import com.intellij.util.indexing.roots.kind.ContentOrigin
import com.intellij.util.indexing.roots.kind.ProjectFileOrDirOrigin
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class ProjectConfigurationFileScanner(private val fileNameSuffix: String) : IndexableFileScanner {
  final override fun startSession(project: Project): IndexableFileScanner.ScanSession {
    val handler = createFileHandler(project)
    return IndexableFileScanner.ScanSession { origin ->
      if (origin is ContentOrigin || origin is ProjectFileOrDirOrigin) {
        IndexableFileScanner.IndexableFileVisitor { file ->
          if (isProjectConfigurationFile(file, fileNameSuffix)) handler(file)
        }
      }
      else null
    }
  }

  protected abstract fun createFileHandler(project: Project): (VirtualFile) -> Unit
}

@ApiStatus.Internal
fun loadProjectConfigurationFiles(project: Project, fileNameSuffix: String): List<String> = if (project.isDefault) listOf() else
  FilenameIndex.getAllFilesByExt(project, fileNameSuffix.removePrefix("."), ProjectScope.getContentScope(project))
    .filter { isProjectConfigurationFile(it, fileNameSuffix) }.map { it.path }

@ApiStatus.Internal
fun isProjectConfigurationFile(file: VirtualFile, fileNameSuffix: String): Boolean {
  if (!file.isInLocalFileSystem || !file.nameSequence.endsWith(fileNameSuffix)) return false
  var parent = file.parent
  while (parent != null) {
    if (StringUtil.equals(parent.nameSequence, ".idea")) return false
    parent = parent.parent
  }
  return true
}
