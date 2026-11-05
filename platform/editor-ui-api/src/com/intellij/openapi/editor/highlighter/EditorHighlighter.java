/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.editor.colors.EditorColorsScheme;
import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.openapi.editor.markup.FontFamilies;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Set;

public interface EditorHighlighter extends DocumentListener {
  @NotNull
  HighlighterIterator createIterator(int startOffset);

  default void setText(@NotNull CharSequence text) {
  }

  void setEditor(@NotNull HighlighterClient editor);

  default void setColorScheme(@NotNull EditorColorsScheme scheme) {
  }

  /**
   * Returns the font families that the text attributes of this highlighter can have,
   * see {@link com.intellij.openapi.editor.markup.TextAttributes#getFontFamilies()}.
   * The set can have values that no token has now. The attributes of the keys of the colors scheme do not count.
   * The set contains a value before the highlighter reports a change of text with that value, see {@link HighlighterClient#repaint}.
   * It is safe to call on any thread, and it returns quickly.
   */
  @ApiStatus.Experimental
  default @Unmodifiable Set<FontFamilies> getTextAttributesFontFamilies() {
    return Set.of();
  }
}
