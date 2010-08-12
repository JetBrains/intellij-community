package com.intellij.psi.impl.source.parsing;

import com.intellij.FileSetTestCase;
import com.intellij.lang.ASTFactory;
import com.intellij.lexer.FilterLexer;
import com.intellij.lexer.JavaLexer;
import com.intellij.lexer.Lexer;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.tree.*;
import com.intellij.psi.tree.IElementType;
import junit.framework.Test;

/**
 *  @author dsl
 */
public class GTTokensTest extends FileSetTestCase {
  public GTTokensTest() {
    super(PathManagerEx.getTestDataPath() + "/psi/gt-tokens");
  }

  public String transform(String testName, String[] data) throws Exception {
    final Lexer lexer = new FilterLexer(new JavaLexer(LanguageLevel.HIGHEST),
                                        new FilterLexer.SetFilter(StdTokenSets.WHITE_SPACE_OR_COMMENT_BIT_SET));
    StringBuffer result = new StringBuffer();
    lexer.start(data[0]);
    final CompositeElement parent = ASTFactory.composite(ElementType.CODE_FRAGMENT);
    while(true) {
      final IElementType tokenType = GTTokens.getTokenType(lexer);
      if (tokenType == null) break;
      TreeElement token = GTTokens.createTokenElementAndAdvance(tokenType, lexer, ((FileElement)parent).getCharTable());
      parent.rawAddChildren(token);
      result.append(token.toString());
      result.append('\n');
    }
    return result.toString();
  }

  public static Test suite() throws Exception { return new GTTokensTest(); }
}
