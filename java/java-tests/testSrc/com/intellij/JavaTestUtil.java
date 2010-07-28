package com.intellij;

import com.intellij.lang.PsiBuilder;
import com.intellij.lang.PsiBuilderFactory;
import com.intellij.lang.StdLanguages;
import com.intellij.lang.java.parser.JavaParserUtil;
import com.intellij.lexer.JavaLexer;
import com.intellij.openapi.application.ex.PathManagerEx;
import com.intellij.pom.java.LanguageLevel;

/**
 * @author yole
 */
public class JavaTestUtil {

  private JavaTestUtil() {
  }

  public static String getJavaTestDataPath() {
    return PathManagerEx.getTestDataPath();
  }

  public static PsiBuilder getJavaBuilder(final CharSequence source) {
    final LanguageLevel languageLevel = LanguageLevel.HIGHEST;
    final JavaLexer lexer = new JavaLexer(languageLevel);
    final PsiBuilder builder = PsiBuilderFactory.getInstance().createBuilder(lexer, StdLanguages.JAVA, source);
    builder.setDebugMode(true);
    JavaParserUtil.setLanguageLevel(builder, languageLevel);
    return builder;
  }
}
