package com.intellij.platform.lsp.impl.features.highlighting

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.impl.cache.LspPerFileCache

internal class LspDocumentHighlightCache(project: Project) : LspPerFileCache<Int, List<TextRangeAndHighlightKind>>(
  project,
  matches = { storedOffset, storedValue, queriedOffset ->
    storedOffset == queriedOffset || storedValue.any { it.textRange.contains(queriedOffset) }
  }
)