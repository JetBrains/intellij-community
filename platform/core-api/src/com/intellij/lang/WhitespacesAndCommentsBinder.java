// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Interface for defining custom element's edge processors for {@linkplain PsiBuilder PSI builder}.
 * Each element has a pair of edge processors: for its left and right edge. Edge processors defines position
 * of element start and end in token stream with recognition of whitespace and comment tokens surrounding the element.
 *
 * @see PsiBuilder.Marker#setCustomEdgeTokenBinders(WhitespacesAndCommentsBinder, WhitespacesAndCommentsBinder)
 */
public interface WhitespacesAndCommentsBinder {
  /**
   * Provides an ability for the processor to get a text of any of given tokens.
   */
  interface TokenTextGetter {
    @NotNull
    CharSequence get(int i);
  }

  /**
   * Recursive binder is allowed to adjust nested elements positions.
   */
  interface RecursiveBinder extends WhitespacesAndCommentsBinder {

  }

  /**
   * Analyzes whitespace and comment tokens at element's edge and returns element's edge position relative to these tokens.
   * Value returned by left edge processor will be used as a pointer to a first token of element.
   * Value returned by right edge processor will be used as a pointer to a token next of element's last token.
   * <p/>
   * <p>Example 1: if a processor for left edge wants to leave all whitespaces and comments out of element's scope
   * (before it's start) it should return value of {@code tokens.size()} placing element's start pointer to a first
   * token after series of whitespaces/comments.
   * <p/>
   * <p>Example 2: if a processor for right edge wants to leave all whitespaces and comments out of element's scope
   * (after its end) it should return value of {@code 0} placing element's end pointer to a first
   * whitespace or comment after element's end.
   *
   * @param tokens       sequence of whitespace and comment tokens at the element's edge.
   * @param atStreamEdge {@code true} if sequence of tokens is located at the beginning or the end of token stream.
   * @param getter       token text getter.
   * @return position of element's edge relative to given tokens.
   */
  int getEdgePosition(List<? extends IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter);
}
