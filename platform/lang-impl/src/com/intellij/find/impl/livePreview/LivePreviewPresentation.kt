// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl.livePreview

import com.intellij.openapi.editor.markup.TextAttributes
import org.jetbrains.annotations.ApiStatus

/** Allows specifying the text attributes for different states of the search occurrences */
@ApiStatus.Experimental
interface LivePreviewPresentation {
  /** Regular search occurence */
  val defaultAttributes: TextAttributes

  /** Currently selected search occurence */
  val cursorAttributes: TextAttributes

  /** Search occurence with empty width (for example, when searching by regex) */
  val emptyRangeAttributes: TextAttributes
    get() = defaultAttributes

  /** Search occurrence excluded from replacement */
  val excludedAttributes: TextAttributes
    get() = defaultAttributes

  /** Regular search occurence inside an editor selection */
  val selectionAttributes: TextAttributes
    get() = defaultAttributes

  /**
   * Highlighter layer, which is used for all search occurrences except currently selected.
   * @see [com.intellij.openapi.editor.markup.HighlighterLayer]
   */
  val defaultLayer: Int

  /**
   * Highlighter layer, which is used for currently selected search occurence.
   * @see [com.intellij.openapi.editor.markup.HighlighterLayer]
   */
  val cursorLayer: Int
}