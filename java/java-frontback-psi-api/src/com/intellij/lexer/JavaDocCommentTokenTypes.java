// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;

/**
 * Describes JDoc comment specific to the Java Plugin (not part of the core-api)
 */
public interface JavaDocCommentTokenTypes extends DocCommentTokenTypes {

  default IElementType codeFence() {
    return commentData();
  }
  default IElementType rightBracket() {
    return commentData();
  }
  default IElementType leftBracket() {
    return commentData();
  }
  default IElementType leftParenthesis() {
    return commentData();
  }
  default IElementType rightParenthesis() {
    return commentData();
  }
  default IElementType sharp() {
    return commentData();
  }
}
