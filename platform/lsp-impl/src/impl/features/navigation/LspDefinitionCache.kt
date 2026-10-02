// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lsp.impl.features.navigation

import com.intellij.openapi.project.Project
import com.intellij.platform.lsp.impl.cache.LspPerFileCache

/**
 * Caches a `textDocument/definition` or a `textDocument/typeDefinition` response.
 *
 * Ctrl+hover asks for the declaration under the mouse pointer on every mouse move,
 * so a lookup also hits the cache while the pointer stays within the origin range reported by the server.
 * A response without an origin range (including an empty one) is only reused at the very same offset.
 */
internal class LspDefinitionCache(project: Project) : LspPerFileCache<Int, TextRangeAndLocationLinks>(
  project,
  matches = { storedOffset, storedValue, queriedOffset ->
    storedValue.textRange.contains(queriedOffset) || storedOffset == queriedOffset
  },
)
