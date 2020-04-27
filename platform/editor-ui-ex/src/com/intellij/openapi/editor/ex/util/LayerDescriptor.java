// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class LayerDescriptor {
  private final SyntaxHighlighter myLayerHighlighter;
  private final String myTokenSeparator;
  private final TextAttributesKey myBackground;

  public LayerDescriptor(@NotNull SyntaxHighlighter layerHighlighter, @NotNull String tokenSeparator, @Nullable TextAttributesKey background) {
    myBackground = background;
    myLayerHighlighter = layerHighlighter;
    myTokenSeparator = tokenSeparator;
  }
  public LayerDescriptor(@NotNull SyntaxHighlighter layerHighlighter, @NotNull String tokenSeparator) {
    this(layerHighlighter, tokenSeparator, null);
  }

  @NotNull
  SyntaxHighlighter getLayerHighlighter() {
    return myLayerHighlighter;
  }

  @NotNull
  String getTokenSeparator() {
    return myTokenSeparator;
  }

  TextAttributesKey getBackgroundKey() {
    return myBackground;
  }
}
