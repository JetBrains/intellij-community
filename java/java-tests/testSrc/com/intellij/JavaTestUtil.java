package com.intellij;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.impl.PsiBuilderImpl;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lexer.JavaLexer;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.JavaTokenType;

/**
 * @author yole
 */
public class JavaTestUtil {

  private JavaTestUtil() {
  }

  public static String getJavaTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  public static PsiBuilder getJavaBuilder(final String source) {
    final LanguageLevel languageLevel = LanguageLevel.HIGHEST;
    final JavaLexer lexer = new JavaLexer(languageLevel);
    final PsiBuilder builder = new PsiBuilderImpl(lexer, JavaTokenType.WHITESPACE_BIT_SET, JavaTokenType.COMMENT_BIT_SET, source);
    JavaParserUtil.setLanguageLevel(builder, languageLevel);
    return builder;
  }
}
