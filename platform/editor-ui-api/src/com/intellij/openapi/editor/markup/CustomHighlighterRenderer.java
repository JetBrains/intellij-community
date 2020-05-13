/*
 * Copyright 2000-2013 JetBrains s.r.o.
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

/*
 * @author max
 */
package com.intellij.openapi.editor.markup;

import com.intellij.openapi.editor.Editor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.*;

/**
 * Specifies custom representation for an editor highlighter.
 *
 * @see RangeHighlighter#setCustomRenderer(CustomHighlighterRenderer)
 * @see RangeHighlighter#getCustomRenderer()
 */
public interface CustomHighlighterRenderer {
  void paint(@NotNull Editor editor, @NotNull RangeHighlighter highlighter, @NotNull Graphics g);

  /**
   * By default (if this method returns {@code false}), custom highlighter is painted over the background (defined by common highlighters)
   * and before the text. If the method returns {@code true}, it will be painted over the text.
   */
  @ApiStatus.Experimental
  default boolean isForeground() {
    return false;
  }
}
