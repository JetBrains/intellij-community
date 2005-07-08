/*
 * Copyright (c) 2000-05 JetBrains s.r.o. All  Rights Reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * -Redistributions of source code must retain the above copyright
 *  notice, this list of conditions and the following disclaimer.
 *
 * -Redistribution in binary form must reproduct the above copyright
 *  notice, this list of conditions and the following disclaimer in
 *  the documentation and/or other materials provided with the distribution.
 *
 * Neither the name of JetBrains or IntelliJ IDEA
 * may be used to endorse or promote products derived from this software
 * without specific prior written permission.
 *
 * This software is provided "AS IS," without a warranty of any kind. ALL
 * EXPRESS OR IMPLIED CONDITIONS, REPRESENTATIONS AND WARRANTIES, INCLUDING
 * ANY IMPLIED WARRANTY OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE
 * OR NON-INFRINGEMENT, ARE HEREBY EXCLUDED. JETBRAINS AND ITS LICENSORS SHALL NOT
 * BE LIABLE FOR ANY DAMAGES OR LIABILITIES SUFFERED BY LICENSEE AS A RESULT
 * OF OR RELATING TO USE, MODIFICATION OR DISTRIBUTION OF THE SOFTWARE OR ITS
 * DERIVATIVES. IN NO EVENT WILL JETBRAINS OR ITS LICENSORS BE LIABLE FOR ANY LOST
 * REVENUE, PROFIT OR DATA, OR FOR DIRECT, INDIRECT, SPECIAL, CONSEQUENTIAL,
 * INCIDENTAL OR PUNITIVE DAMAGES, HOWEVER CAUSED AND REGARDLESS OF THE THEORY
 * OF LIABILITY, ARISING OUT OF THE USE OF OR INABILITY TO USE SOFTWARE, EVEN
 * IF JETBRAINS HAS BEEN ADVISED OF THE POSSIBILITY OF SUCH DAMAGES.
 *
 */

package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

/**
 * The IDEA side of a custom language parser. Provides lexing results to the
 * plugin and allows the plugin to build the AST tree.
 */

public interface PsiBuilder {
  /**
   * Returns the complete text being parsed.
   * @return the text being parsed
   */
  CharSequence getOriginalText();

  /**
   * Advances the lexer to the next token, skipping whitespace and comment tokens.
   */
  void advanceLexer();

  /**
   * Returns the type of current token from the lexer.
   * @return the token type, or null when lexing is over.
   */
  @Nullable(documentation = "Returns null when lexing is over")
  IElementType getTokenType();

  /**
   * Returns the text of the current token from the lexer.
   * @return the token text, or null when the lexing is over.
   */
  @Nullable(documentation = "Returns null when lexing is over")
  String getTokenText();

  /**
   * Returns the start offset of the current token, or the file length when the lexing is over.
   * @return the token offset.
   */
  int getCurrentOffset();

  interface Marker {
    Marker preceed();
    void drop();
    void rollbackTo();
    void done(IElementType type);
  }

  Marker mark();

  /**
   * Adds an error marker with the specified message text at the current position in the tree.
   * @param messageText the text of the error message displayed to the user.
   */
  void error(String messageText);

  /**
   * Checks if the lexer has reached the end of file.
   * @return true if the lexer is at end of file, false otherwise.
   */
  boolean eof();

  /**
   * Returns the result of the parsing. All markers must be completed or dropped before this method is called.
   * @return the built tree.
   */
  ASTNode getTreeBuilt();

  /**
   * Enables or disables the builder debug mode. In debug mode, the builder will print stack trace
   * to marker allocation position if one is not done when calling getTreeBuilt().
   * @param dbgMode the debug mode value.
   */
  void setDebugMode(boolean dbgMode);
}
