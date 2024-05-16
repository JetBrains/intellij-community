// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.combined.search

import com.intellij.diff.tools.combined.CombinedDiffViewer
import com.intellij.openapi.editor.Editor
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

/**
 * Provides "search in multiple editors" functionality for [CombinedDiffViewer]
 *
 *  @see [com.intellij.openapi.diff.impl.combined.search.CombinedDiffSearchEditorActionHandler]
 *  @see [com.intellij.diff.tools.combined.CombinedDiffModelListener.onRequestsLoaded]
 */
@ApiStatus.Internal
interface CombinedDiffSearchProvider {

  fun installSearch(viewer: CombinedDiffViewer)
}

/**
 * Controller to update search in particular [com.intellij.diff.tools.combined.CombinedDiffMainUI]
 */
@ApiStatus.Internal
interface CombinedDiffSearchController {

  val searchComponent: JComponent

  fun update(context: CombinedDiffSearchContext)
}

@ApiStatus.Internal
class CombinedDiffSearchContext(val holders: List<EditorHolder>) {
  /**
   * Holder, which contains ordered editors
   *
   * @see [com.intellij.diff.EditorDiffViewer]
   */
  class EditorHolder(val editors: List<Editor>)
}
