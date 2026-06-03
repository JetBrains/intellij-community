package com.intellij.platform.lsp.impl.features.documentation

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.lsp.impl.cache.LspPerFileCache
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread

internal class HoverResultCache(project: Project) : LspPerFileCache<Int, TextRangeAndMarkupContent>(
  project,
  matches = { _, storedValue, queriedOffset -> storedValue.textRange.contains(queriedOffset) },
) {

  @RequiresBackgroundThread
  override fun getOrCompute(file: VirtualFile, key: Int, compute: () -> TextRangeAndMarkupContent?): TextRangeAndMarkupContent? {
    if (file is VirtualFileWindow) {
      thisLogger().error("VirtualFileWindow not expected here")
      return null
    }
    return super.getOrCompute(file, key, compute)
  }
}
