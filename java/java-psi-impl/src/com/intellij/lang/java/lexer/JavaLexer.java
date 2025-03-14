// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.lexer;

import com.intellij.platform.syntax.psi.lexer.LexerAdapter;
import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.util.PsiUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

  /**
   * @param keyword soft keyword
   * @return JavaFeature, which introduced a given keyword; null if the supplied string is not a soft keyword
   * @deprecated use {@link PsiUtil#softKeywordFeature(CharSequence)}
   */
  @ApiStatus.ScheduledForRemoval
  @Deprecated
  public static @Nullable JavaFeature softKeywordFeature(@NotNull CharSequence keyword) {
    return PsiUtil.softKeywordFeature(keyword);
  }
}
