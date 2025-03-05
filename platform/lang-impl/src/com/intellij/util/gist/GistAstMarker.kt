// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.gist

import com.intellij.lang.LighterAST
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.source.PsiFileImpl
import com.intellij.psi.impl.source.tree.FileElement
import com.intellij.util.AstLoadingFilter
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.io.DataExternalizer
import java.io.DataInput
import java.io.DataOutput

private typealias GistMarkerData = Result<Boolean>

private val ACCEPTS_YES: GistMarkerData = GistMarkerData.success(true)
private val ACCEPTS_NO: GistMarkerData = GistMarkerData.success(false)

/**
 * Lightweight replacement for indexing, computes if a file is accepted by marker function on-demand and stores this marker on disk.
 * Typical usage: check if a file should have a desired icon based on its content in [com.intellij.ide.IconProvider].
 *
 * @see GistManager
 */
class GistAstMarker(private val fileType: FileType,
                    private val gistId: String,
                    function: (LighterAST) -> Boolean) {

  private val gist = GistManager.getInstance().newVirtualFileGist(gistId, 0, YesNoExternalizer) { project, file ->
    if (project == null) return@newVirtualFileGist ACCEPTS_NO

    val viewProvider = PsiManager.getInstance(project).findViewProvider(file)
    if (viewProvider == null) return@newVirtualFileGist ACCEPTS_NO

    val files = viewProvider.allFiles
    for (f in files) {
      if (f is PsiFileImpl && f.fileType == fileType) {
        val astTree = AstLoadingFilter.forceAllowTreeLoading<FileElement, Throwable>(f) {
          f.calcTreeElement()
        }

        val ast = astTree.lighterAST
        if (function(ast)) {
          return@newVirtualFileGist ACCEPTS_YES
        }
      }
    }

    ACCEPTS_NO
  }

  @RequiresReadLock
  fun accepts(project: Project?, virtualFile: VirtualFile): Boolean {
    if (virtualFile.fileType != fileType) return false

    return gist.getFileData(project, virtualFile) == ACCEPTS_YES
  }

  override fun toString(): String {
    return "GistAstMarker(gistId='$gistId', fileType=$fileType)"
  }
}

private object YesNoExternalizer : DataExternalizer<GistMarkerData> {
  override fun save(out: DataOutput, value: GistMarkerData) {
    out.writeBoolean(value == ACCEPTS_YES)
  }

  override fun read(`in`: DataInput): GistMarkerData {
    val isWM = `in`.readBoolean()
    return if (isWM) ACCEPTS_YES else ACCEPTS_NO
  }
}
