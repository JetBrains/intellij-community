// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.impl.livePreview

import com.intellij.openapi.editor.markup.TextAttributes

interface LivePreviewPresentation {
  val defaultAttributes: TextAttributes
  val cursorAttributes: TextAttributes
  val emptyRangeAttributes: TextAttributes
    get() = defaultAttributes
  val excludedAttributes: TextAttributes
    get() = defaultAttributes
  val selectionAttributes: TextAttributes
    get() = defaultAttributes

  /** [com.intellij.openapi.editor.markup.HighlighterLayer] */
  val defaultLayer: Int
  val cursorLayer: Int
}