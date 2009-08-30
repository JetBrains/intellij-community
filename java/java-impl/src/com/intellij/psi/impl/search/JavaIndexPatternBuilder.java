package com.intellij.psi.impl.search;

import com.intellij.lexer.Lexer;
import com.intellij.lexer.JavaLexer;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.JavaDocTokenType;
import com.intellij.psi.xml.XmlTokenType;
import com.intellij.psi.xml.XmlElementType;
import com.intellij.psi.impl.source.tree.StdTokenSets;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.tree.TokenSet;
import com.intellij.psi.tree.IElementType;
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
    return 0;
  }
}
