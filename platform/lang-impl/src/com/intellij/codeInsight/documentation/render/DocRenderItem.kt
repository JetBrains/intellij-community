// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render

import com.intellij.openapi.editor.CustomFoldRegion
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.platform.backend.documentation.DocumentationTarget
import com.intellij.platform.backend.documentation.InlineDocumentation
import org.jetbrains.annotations.Nls

interface DocRenderItem {
  val textToRender: @Nls String?

  val foldRegion: CustomFoldRegion?

  val highlighter: RangeHighlighter

  val editor: Editor

  fun calcFoldingGutterIconRenderer(): GutterIconRenderer?

  fun setIconVisible(visible: Boolean)

  fun toggle()

  fun getInlineDocumentation(): InlineDocumentation?

  fun getInlineDocumentationTarget(): DocumentationTarget?
}