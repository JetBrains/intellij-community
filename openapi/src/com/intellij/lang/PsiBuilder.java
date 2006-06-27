/*
 * Copyright 2000-2005 JetBrains s.r.o.
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

package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import com.intellij.openapi.util.UserDataHolder;
import org.jetbrains.annotations.Nullable;

/**
 * The IDEA side of a custom language parser. Provides lexing results to the
 * plugin and allows the plugin to build the AST tree.
 *
 * @see PsiParser
 * @see ASTNode
 */

public interface PsiBuilder extends UserDataHolder {
  /**
   * Returns the complete text being parsed.
   *
   * @return the text being parsed
   */
  CharSequence getOriginalText();

  /**
   * Advances the lexer to the next token, skipping whitespace and comment tokens.
   */
  void advanceLexer();

  /**
   * Returns the type of current token from the lexer.
   *
   * @return the token type, or null when lexing is over.
   */
  @Nullable
  IElementType getTokenType();

  /**
   * Returns the text of the current token from the lexer.
   *
   * @return the token text, or null when the lexing is over.
   */
  @Nullable
  String getTokenText();

  /**
   * Returns the start offset of the current token, or the file length when the lexing is over.
   *
   * @return the token offset.
   */
  int getCurrentOffset();

  /**
   * A marker defines a range in the document text which becomes a node in the AST
   * tree. The ranges defined by markers within the text range of the current marker
   * become child nodes of the node defined by the current marker.
   */
  interface Marker {
    /**
     * Creates and returns a new marker starting immediately before the start of
     * this marker and extending after its end. Can be called only on a completed marker.
     *
     * @return the new marker instance.
     */
    Marker precede();

    /**
     * Drops this marker. Can be called after other markers have been added and completed
     * after this marker. Does not affect lexer position or markers added after this marker.
     */
    void drop();

    /**
     * Drops this marker and all markers added after it, and reverts the lexer position to the
     * position of this marker.
     */
    void rollbackTo();

    /**
     * Completes this marker and labels it with the specified AST node type. Before calling this method,
     * all markers added after the beginning of this marker must be either dropped or completed.
     *
     * @param type the type of the node in the AST tree.
     */
    void done(IElementType type);
  }

  /**
   * Creates a marker at the current parsing position.
   *
   * @return the new marker instance.
   */
  Marker mark();

  /**
   * Adds an error marker with the specified message text at the current position in the tree.
   *
   * @param messageText the text of the error message displayed to the user.
   */
  void error(String messageText);

  /**
   * Checks if the lexer has reached the end of file.
   *
   * @return true if the lexer is at end of file, false otherwise.
   */
  boolean eof();

  /**
   * Returns the result of the parsing. All markers must be completed or dropped before this method is called.
   *
   * @return the built tree.
   */
  ASTNode getTreeBuilt();

  /**
   * Enables or disables the builder debug mode. In debug mode, the builder will print stack trace
   * to marker allocation position if one is not done when calling getTreeBuilt().
   *
   * @param dbgMode the debug mode value.
   */
  void setDebugMode(boolean dbgMode);
}
