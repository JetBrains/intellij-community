/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.psi.impl.search;

import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.JavaTokenType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.xml.XmlTokenType;
import org.jetbrains.annotations.Nullable;

/**
 * @author yole
 */
public class JavaIndexPatternBuilder implements IndexPatternBuilder {
  public static final TokenSet XML_DATA_CHARS = TokenSet.create(XmlTokenType.XML_DATA_CHARACTERS);
  public static final TokenSet XML_COMMENT_BIT_SET = TokenSet.create(XmlElementType.XML_COMMENT_CHARACTERS);

  @Nullable
  public Lexer getIndexingLexer(final PsiFile file) {
    if (file instanceof PsiJavaFile && !(file instanceof JspFile)) {
      return new JavaLexer(((PsiJavaFile)file).getLanguageLevel());
    }
    return null;
  }

  @Nullable
  public TokenSet getCommentTokenSet(final PsiFile file) {
    if (file instanceof PsiJavaFile && !(file instanceof JspFile)) {
      return TokenSet.orSet(StdTokenSets.COMMENT_BIT_SET, XML_COMMENT_BIT_SET, JavaDocTokenType.ALL_JAVADOC_TOKENS, XML_DATA_CHARS);
    }
    return null;
  }

  public int getCommentStartDelta(final IElementType tokenType) {
    return 0;
  }

  public int getCommentEndDelta(final IElementType tokenType) {
    return tokenType == JavaTokenType.C_STYLE_COMMENT ? "*/".length() : 0;
  }
}
