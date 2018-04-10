/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
package com.intellij.lang;

import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WhitespacesBinders {
  private WhitespacesBinders() { }

  public static final WhitespacesAndCommentsBinder DEFAULT_LEFT_BINDER = new WhitespacesAndCommentsBinder() {
    public int getEdgePosition(List<IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
      return tokens.size();
    }
  };
  public static final WhitespacesAndCommentsBinder DEFAULT_RIGHT_BINDER = new WhitespacesAndCommentsBinder() {
    public int getEdgePosition(List<IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
      return 0;
    }
  };

  public static final WhitespacesAndCommentsBinder GREEDY_LEFT_BINDER = DEFAULT_RIGHT_BINDER;
  public static final WhitespacesAndCommentsBinder GREEDY_RIGHT_BINDER = DEFAULT_LEFT_BINDER;

  public static WhitespacesAndCommentsBinder leadingCommentsBinder(@NotNull TokenSet commentTypes) {
    return new WhitespacesAndCommentsBinder() {
      @Override
      public int getEdgePosition(List<IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
        int i = 0;
        while (i < tokens.size() && !commentTypes.contains(tokens.get(i))) {
          i++;
        }
        return i;
      }
    };
  }

  public static WhitespacesAndCommentsBinder trailingCommentsBinder(@NotNull TokenSet commentTypes) {
    return new WhitespacesAndCommentsBinder() {
      @Override
      public int getEdgePosition(List<IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
        int i = tokens.size() - 1;
        while (i >= 0 && !commentTypes.contains(tokens.get(i))) {
          i--;
        }
        return i + 1;
      }
    };
  }
}