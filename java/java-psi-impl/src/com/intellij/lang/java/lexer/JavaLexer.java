// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.lexer;

import com.intellij.platform.syntax.psi.lexer.LexerAdapter;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.NotNull;

import static com.intellij.lang.java.syntax.JavaElementTypeConverterKt.getJavaElementTypeConverter;

/**
 * @deprecated Use the new Java syntax library instead.
 *             See {@link com.intellij.java.syntax.JavaSyntaxDefinition#createLexer(LanguageLevel)}
 */
@Deprecated
public final class JavaLexer extends LexerAdapter {
  public JavaLexer(@NotNull LanguageLevel level) {
    super(new com.intellij.java.syntax.lexer.JavaLexer(level), getJavaElementTypeConverter());
  }

  /**
   * @deprecated use {@link PsiUtil#isKeyword(String, LanguageLevel)}
   */
  @Deprecated
  public static boolean isKeyword(@NotNull String id, @NotNull LanguageLevel level) {
    return PsiUtil.isKeyword(id, level);
  }

  /**
   * @param id keyword candidate
   * @param level current language level
   * @return true if a given id is a keyword at a given language level
   * @deprecated use {@link PsiUtil#isSoftKeyword(CharSequence, LanguageLevel)}
   */
  @Deprecated
  public static boolean isSoftKeyword(@NotNull CharSequence id, @NotNull LanguageLevel level) {
    return PsiUtil.isSoftKeyword(id, level);
  }
}
