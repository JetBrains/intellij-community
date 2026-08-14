// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.analysisignore

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.indexing.roots.IndexableFileScanner
import com.intellij.util.indexing.roots.kind.ContentOrigin
import com.intellij.util.indexing.roots.kind.ProjectFileOrDirOrigin
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

@ApiStatus.Internal
class AnalysisIgnoreIndexableFileScanner : IndexableFileScanner {
  override fun startSession(project: Project): IndexableFileScanner.ScanSession {
    if (!Registry.`is`(ANALYSIS_IGNORE_ENABLED_KEY, true)) {
      return IndexableFileScanner.ScanSession { null }
    }

    val service = AnalysisIgnoreService.getInstance(project)
    val knownBaseDirUrls = ConcurrentHashMap.newKeySet<String>().apply { addAll(service.knownBaseDirUrls()) }

    return IndexableFileScanner.ScanSession { origin ->
      if (origin is ContentOrigin || origin is ProjectFileOrDirOrigin) {
        IndexableFileScanner.IndexableFileVisitor { fileOrDir -> service.visit(fileOrDir, knownBaseDirUrls) }
      }
      else {
        null
      }
    }
  }
}

private fun AnalysisIgnoreService.visit(fileOrDir: VirtualFile, knownBaseDirUrls: MutableSet<String>) {
  if (!fileOrDir.isDirectory || !fileOrDir.isValid) return

  val file = fileOrDir.findChild(ANALYSIS_IGNORE_FILE_NAME)?.takeUnless { it.isDirectory }
  if (file != null) {
    applyNow(file)
  }
  else if (knownBaseDirUrls.remove(fileOrDir.url)) {
    forgetNow(fileOrDir.url)
  }
}
