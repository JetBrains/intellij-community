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

package com.intellij.openapi.editor.highlighter;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;


public interface HighlighterIterator {
  TextAttributes getTextAttributes();

  @ApiStatus.Experimental
  default TextAttributesKey @NotNull [] getTextAttributesKeys() {
    return TextAttributesKey.EMPTY_ARRAY;
  }

  int getStart();
  int getEnd();

  /**
   * @return type if the current token
   */
  IElementType getTokenType();

  /**
   * Move iterator to the next segment
   */
  void advance();

  /**
   * Move iterator to the previous segment
   */
  void retreat();
  boolean atEnd();
  Document getDocument();
}
