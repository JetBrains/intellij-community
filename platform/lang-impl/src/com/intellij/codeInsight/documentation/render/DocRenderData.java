// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.codeInsight.documentation.render;

import com.intellij.openapi.editor.CustomFoldRegion;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface DocRenderData {
  @Nls
  @Nullable String getTextToRender();

  @Nullable CustomFoldRegion getFoldRegion();

  @NotNull RangeHighlighter getHighlighter();

  @NotNull Editor getEditor();

  GutterIconRenderer calcGutterIconRenderer();

  default void setIconVisible(boolean visible) { }
}