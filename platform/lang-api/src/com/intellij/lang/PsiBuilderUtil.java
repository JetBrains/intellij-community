/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.Nullable;


public class PsiBuilderUtil {
  private PsiBuilderUtil() { }

  /**
   * Returns type of next token.
   *
   * @param builder PSI builder to operate on.
   * @return type of next token or null, if the builder is at the end of token stream
   */
  @Nullable
  public static IElementType nextTokenType(final PsiBuilder builder) {
    if (builder.getTokenType() == null) return null;
    final PsiBuilder.Marker sp = builder.mark();
    builder.advanceLexer();
    final IElementType result = builder.getTokenType();
    sp.rollbackTo();
    return result;
  }

  /**
   * Checks if tokens in token stream form expected sequence.
   *
   * @param builder PSI builder to operate on.
   * @param tokenTypes expected elements.
   * @return true if tokens form expected sequence, false otherwise
   */
  public static boolean lookAhead(final PsiBuilder builder, final IElementType... tokenTypes) {
    if (tokenTypes.length == 0) return true;

    if (!tokenTypes[0].equals(builder.getTokenType())) return false;

    if (tokenTypes.length == 1) return true;

    final PsiBuilder.Marker rb = builder.mark();
    builder.advanceLexer();
    int i = 1;
    while (!builder.eof() && i < tokenTypes.length && tokenTypes[i].equals(builder.getTokenType())) {
      builder.advanceLexer();
      i++;
    }
    rb.rollbackTo();
    return i == tokenTypes.length;
  }

  /**
   * Advances lexer by given number of tokens (but not beyond the end of token stream).
   *
   * @param builder PSI builder to operate on.
   * @param count number of tokens to skip.
   */
  public static void advance(final PsiBuilder builder, final int count) {
    for (int i=0; i<count && !builder.eof(); i++) {
      builder.getTokenType();
      builder.advanceLexer();
    }
  }

  /**
   * Advances lexer if current token is of expected type, does nothing otherwise.
   *
   * @param builder PSI builder to operate on.
   * @param expectedType expected token.
   * @return true if token matches, false otherwise.
   */
  public static boolean expect(final PsiBuilder builder, final IElementType expectedType) {
    if (builder.getTokenType() == expectedType) {
      builder.advanceLexer();
      return true;
    }
    return false;
  }

  /**
   * Advances lexer if current token is of expected type, does nothing otherwise.
   *
   * @param builder PSI builder to operate on.
   * @param expectedTypes expected token types.
   * @return true if token matches, false otherwise.
   */
  public static boolean expect(final PsiBuilder builder, final TokenSet expectedTypes) {
    if (expectedTypes.contains(builder.getTokenType())) {
      builder.advanceLexer();
      return true;
    }
    return false;
  }

  /**
   * Release group of allocated markers.
   *
   * @param markers markers to drop.
   */
  public static void drop(final PsiBuilder.Marker... markers) {
    for (PsiBuilder.Marker marker : markers) {
      marker.drop();
    }
  }
}
