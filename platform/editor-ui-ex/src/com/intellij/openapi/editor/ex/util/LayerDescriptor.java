/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.fileTypes.SyntaxHighlighter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author max
 */
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
