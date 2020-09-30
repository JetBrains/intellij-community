// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi;

import com.intellij.lang.Language;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.IFileElementType;

/**
 * The standard lexer token types common to all languages.
 */

public interface TokenType {
  /**
   * Token type for a sequence of whitespace characters.
   */
  IElementType WHITE_SPACE = new WhiteSpaceTokenType();

  /**
   * Token type for a character which is not valid in the position where it was encountered,
   * according to the language grammar.
   */
  IElementType BAD_CHARACTER = new IElementType("BAD_CHARACTER", Language.ANY);

  /**
   * Internal token type used by the code formatter.
   */
  IElementType NEW_LINE_INDENT = new IElementType("NEW_LINE_INDENT", Language.ANY);

  IElementType ERROR_ELEMENT = new IElementType("ERROR_ELEMENT", Language.ANY) {
    @Override
    public boolean isLeftBound() {
      return true;
    }
  };

  IElementType CODE_FRAGMENT = new IFileElementType("CODE_FRAGMENT", Language.ANY);
  IElementType DUMMY_HOLDER = new IFileElementType("DUMMY_HOLDER", Language.ANY);
}
