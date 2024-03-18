// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.search;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.JavaDocElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;


public final class JavaIndexPatternBuilder implements IndexPatternBuilder {
  public static final TokenSet XML_DATA_CHARS = TokenSet.create(XmlTokenType.XML_DATA_CHARACTERS);
  public static final TokenSet XML_COMMENT_BIT_SET = TokenSet.create(XmlTokenType.XML_COMMENT_CHARACTERS);

  @Override
  @Nullable
  public Lexer getIndexingLexer(@NotNull final PsiFile file) {
    if (file instanceof PsiJavaFile && !(file instanceof JspFile)) {
      return JavaParserDefinition.createLexer(((PsiJavaFile)file).getLanguageLevel());
    }
    return null;
  }

  @Override
  @Nullable
  public TokenSet getCommentTokenSet(@NotNull final PsiFile file) {
    if (file instanceof PsiJavaFile && !(file instanceof ServerPageFile)) {
      return TokenSet.orSet(StdTokenSets.COMMENT_BIT_SET, XML_COMMENT_BIT_SET, JavaDocTokenType.ALL_JAVADOC_TOKENS, XML_DATA_CHARS);
    }
    return null;
  }

  @Override
  public int getCommentStartDelta(final IElementType tokenType) {
    return tokenType == JavaTokenType.END_OF_LINE_COMMENT || tokenType == JavaTokenType.C_STYLE_COMMENT
           ? 2 : tokenType == JavaDocElementType.DOC_COMMENT ? 3 : 0;
  }

  @Override
  public int getCommentEndDelta(final IElementType tokenType) {
    return tokenType == JavaTokenType.C_STYLE_COMMENT || tokenType == JavaDocElementType.DOC_COMMENT ? 2 : 0;
  }

  @NotNull
  @Override
  public String getCharsAllowedInContinuationPrefix(@NotNull IElementType tokenType) {
    return tokenType == JavaTokenType.C_STYLE_COMMENT || tokenType == JavaDocElementType.DOC_COMMENT ? "*" : "";
  }
}
