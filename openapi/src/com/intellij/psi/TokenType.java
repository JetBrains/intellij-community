/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IElementType;

/**
 * The standard lexer token types common to all languages.
 */

public interface TokenType {
  /**
   * Token type for a sequence of whitespace characters.
   */
  IElementType WHITE_SPACE = new IElementType("WHITE_SPACE", Language.ANY);

  /**
   * Token type for a character which is not valid in the position where it was encountered,
   * according to the language grammar.
   */
  IElementType BAD_CHARACTER = new IElementType("BAD_CHARACTER", Language.ANY);

  /**
   * Internal token type used by the code formatter.
   */
  IElementType NEW_LINE_INDENT = new IElementType("NEW_LINE_INDENT", Language.ANY);
}
