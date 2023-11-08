// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.source;

import com.intellij.lang.WhitespacesAndCommentsBinder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.ParentAwareTokenSet;
import com.intellij.psi.tree.TokenSet;

import java.util.List;

import static com.intellij.psi.impl.source.BasicElementTypes.BASIC_JAVA_PLAIN_COMMENT_BIT_SET;
import static com.intellij.psi.impl.source.BasicJavaDocElementType.BASIC_DOC_COMMENT;
import static com.intellij.psi.tree.ParentAwareTokenSet.create;
import static com.intellij.psi.tree.ParentAwareTokenSet.orSet;

public class WhiteSpaceAndCommentSetHolder {
  public static final WhiteSpaceAndCommentSetHolder INSTANCE = new WhiteSpaceAndCommentSetHolder();
  private static final ParentAwareTokenSet PRECEDING_COMMENT_SET =
    orSet(create(BasicJavaElementType.BASIC_MODULE), BasicElementTypes.BASIC_FULL_MEMBER_BIT_SET);

  private static final ParentAwareTokenSet TRAILING_COMMENT_SET =
    orSet(create(BasicJavaElementType.BASIC_PACKAGE_STATEMENT), BasicElementTypes.BASIC_IMPORT_STATEMENT_BASE_BIT_SET,
          BasicElementTypes.BASIC_FULL_MEMBER_BIT_SET, BasicElementTypes.BASIC_JAVA_STATEMENT_BIT_SET);

  private WhiteSpaceAndCommentSetHolder() {
  }

  private final WhitespacesAndCommentsBinder PRECEDING_COMMENT_BINDER = new PrecedingWhitespacesAndCommentsBinder(false);
  private final WhitespacesAndCommentsBinder SPECIAL_PRECEDING_COMMENT_BINDER = new PrecedingWhitespacesAndCommentsBinder(true);
  private final WhitespacesAndCommentsBinder TRAILING_COMMENT_BINDER = new TrailingWhitespacesAndCommentsBinder();

  public WhitespacesAndCommentsBinder getPrecedingCommentBinder() {
    return PRECEDING_COMMENT_BINDER;
  }

  public WhitespacesAndCommentsBinder getSpecialPrecedingCommentBinder() {
    return SPECIAL_PRECEDING_COMMENT_BINDER;
  }

  public WhitespacesAndCommentsBinder getTrailingCommentBinder() {
    return TRAILING_COMMENT_BINDER;
  }

  public ParentAwareTokenSet getPrecedingCommentSet() {
    return PRECEDING_COMMENT_SET;
  }

  public ParentAwareTokenSet getTrailingCommentSet() {
    return TRAILING_COMMENT_SET;
  }


  private static class PrecedingWhitespacesAndCommentsBinder implements WhitespacesAndCommentsBinder {
    private final boolean myAfterEmptyImport;

    PrecedingWhitespacesAndCommentsBinder(final boolean afterImport) {
      this.myAfterEmptyImport = afterImport;
    }

    @Override
    public int getEdgePosition(final List<? extends IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter
      getter) {
      if (tokens.isEmpty()) return 0;

      // 1. bind doc comment
      for (int idx = tokens.size() - 1; idx >= 0; idx--) {
        if (BasicJavaAstTreeUtil.is(tokens.get(idx), BASIC_DOC_COMMENT)) return idx;
      }

      // 2. bind plain comments
      int result = tokens.size();
      for (int idx = tokens.size() - 1; idx >= 0; idx--) {
        final IElementType tokenType = tokens.get(idx);
        if (TokenSet.WHITE_SPACE.contains(tokenType)) {
          if (StringUtil.getLineBreakCount(getter.get(idx)) > 1) break;
        }
        else if (BASIC_JAVA_PLAIN_COMMENT_BIT_SET.contains(tokenType)) {
          if (atStreamEdge ||
              (idx == 0 && myAfterEmptyImport) ||
              (idx > 0 && TokenSet.WHITE_SPACE.contains(tokens.get(idx - 1)) && StringUtil.containsLineBreak(getter.get(idx - 1)))) {
            result = idx;
          }
        }
        else {
          break;
        }
      }

      return result;
    }
  }

  private static class TrailingWhitespacesAndCommentsBinder implements WhitespacesAndCommentsBinder {

    @Override
    public int getEdgePosition(final List<? extends IElementType> tokens, final boolean atStreamEdge, final TokenTextGetter getter) {
      if (tokens.isEmpty()) return 0;

      int result = 0;
      for (int idx = 0; idx < tokens.size(); idx++) {
        final IElementType tokenType = tokens.get(idx);
        if (TokenSet.WHITE_SPACE.contains(tokenType)) {
          if (StringUtil.containsLineBreak(getter.get(idx))) break;
        }
        else if (BASIC_JAVA_PLAIN_COMMENT_BIT_SET.contains(tokenType)) {
          result = idx + 1;
        }
        else {
          break;
        }
      }

      return result;
    }
  }
}
