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
package com.intellij.lang;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class WhitespacesBinders {
  public static final WhitespacesAndCommentsBinder DEFAULT_RIGHT_BINDER = new WhitespacesAndCommentsBinder() {
    public int getEdgePosition(List<IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
      return 0;
    }
  };
  public static final WhitespacesAndCommentsBinder DEFAULT_LEFT_BINDER = new WhitespacesAndCommentsBinder() {
    public int getEdgePosition(List<IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
      return tokens.size();
    }
  };

  public static final WhitespacesAndCommentsBinder GREEDY_LEFT_BINDER = DEFAULT_RIGHT_BINDER;
  public static final WhitespacesAndCommentsBinder GREEDY_RIGHT_BINDER = DEFAULT_LEFT_BINDER;

  public static WhitespacesAndCommentsBinder leadingCommentsBinder(@NotNull Condition<IElementType> isCommentCondition) {
    return new LeadingCommentsBinder(isCommentCondition);
  }

  public static WhitespacesAndCommentsBinder leadingCommentsBinder(IElementType... commentTypes) {
    return leadingCommentsBinder(TokenSet.create(commentTypes));
  }

  public static WhitespacesAndCommentsBinder leadingCommentsBinder(@NotNull final TokenSet commentTypes) {
    return leadingCommentsBinder(new Condition<IElementType>() {
      @Override
      public boolean value(IElementType type) {
        return commentTypes.contains(type);
      }
    });
  }

  private static class LeadingCommentsBinder implements WhitespacesAndCommentsBinder {
    @NotNull private final Condition<IElementType> myIsCommentCondition;

    LeadingCommentsBinder(@NotNull Condition<IElementType> isCommentCondition) {
      myIsCommentCondition = isCommentCondition;
    }

    @Override
    public int getEdgePosition(List<IElementType> tokens, boolean atStreamEdge, TokenTextGetter getter) {
      int i = 0;
      while (i < tokens.size() && !myIsCommentCondition.value(tokens.get(i))) {
        i++;
      }
      return i;
    }
  }

  private WhitespacesBinders() {
  }
}
