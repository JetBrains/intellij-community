// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notebooks.ui.visualization

import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.observable.properties.ObservableProperty
import com.intellij.ui.JBColor
import java.awt.Color

interface NotebookEditorAppearanceColors {

  val editorBackgroundColor: ObservableProperty<Color>

  val caretRowBackgroundColor: ObservableProperty<Color?>

  val codeCellBackgroundColor: ObservableProperty<Color>

  val cellStripeHoveredColor: ObservableProperty<Color>

  val cellStripeSelectedColor: ObservableProperty<Color>

  val cellFrameSelectedColor: ObservableProperty<Color>

  val cellFrameHoveredColor: ObservableProperty<Color>

  val cellPopupToolbarBorderColor: ObservableProperty<Color>

  fun getGutterInputExecutionCountForegroundColor(scheme: EditorColorsScheme): Color? = null
  fun getGutterOutputExecutionCountForegroundColor(scheme: EditorColorsScheme): Color? = null
  fun getProgressStatusRunningColor(scheme: EditorColorsScheme): Color = JBColor.BLUE
  fun getInlayBackgroundColor(scheme: EditorColorsScheme): Color? = null
  fun getTextOutputBackground(scheme: EditorColorsScheme): Color = scheme.defaultBackground
}