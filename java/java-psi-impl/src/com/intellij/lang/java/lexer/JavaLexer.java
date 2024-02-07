// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.lexer;

import com.intellij.pom.java.JavaFeature;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.tree.JavaDocElementTypeFactory;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.Set;

import static com.intellij.psi.PsiKeyword.*;

public final class JavaLexer extends BasicJavaLexer {

  private static final Set<String> KEYWORDS = ContainerUtil.immutableSet(
    ABSTRACT, BOOLEAN, BREAK, BYTE, CASE, CATCH, CHAR, CLASS, CONST, CONTINUE, DEFAULT, DO, DOUBLE, ELSE, EXTENDS, FINAL, FINALLY,
    FLOAT, FOR, GOTO, IF, IMPLEMENTS, IMPORT, INSTANCEOF, INT, INTERFACE, LONG, NATIVE, NEW, PACKAGE, PRIVATE, PROTECTED, PUBLIC,
    RETURN, SHORT, STATIC, STRICTFP, SUPER, SWITCH, SYNCHRONIZED, THIS, THROW, THROWS, TRANSIENT, TRY, VOID, VOLATILE, WHILE,
    TRUE, FALSE, NULL, NON_SEALED);

  private static final @NotNull Map<CharSequence, JavaFeature> SOFT_KEYWORDS = CollectionFactory.createCharSequenceMap(true);

  static {
    SOFT_KEYWORDS.put(VAR, JavaFeature.LVTI);
    SOFT_KEYWORDS.put(RECORD, JavaFeature.RECORDS);
    SOFT_KEYWORDS.put(YIELD, JavaFeature.SWITCH_EXPRESSION);
    SOFT_KEYWORDS.put(SEALED, JavaFeature.SEALED_CLASSES);
    SOFT_KEYWORDS.put(PERMITS, JavaFeature.SEALED_CLASSES);
    SOFT_KEYWORDS.put(WHEN, JavaFeature.PATTERN_GUARDS_AND_RECORD_PATTERNS);
    SOFT_KEYWORDS.put(OPEN, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(MODULE, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(REQUIRES, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(EXPORTS, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(OPENS, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(USES, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(PROVIDES, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(TRANSITIVE, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(TO, JavaFeature.MODULES);
    SOFT_KEYWORDS.put(WITH, JavaFeature.MODULES);
  }

  public JavaLexer(@NotNull LanguageLevel level) {
    super(level, JavaDocElementTypeFactory.INSTANCE);
  }
  public static boolean isKeyword(@NotNull String id, @NotNull LanguageLevel level) {
    return KEYWORDS.contains(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_4) && ASSERT.equals(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_5) && ENUM.equals(id);
  }

  /**
   * @param id keyword candidate
   * @param level current language level
   * @return true if a given id is a keyword at a given language level
   */
  public static boolean isSoftKeyword(@NotNull CharSequence id, @NotNull LanguageLevel level) {
    JavaFeature feature = softKeywordFeature(id);
    return feature != null && feature.isSufficient(level);
  }

  /**
   * @param keyword soft keyword
   * @return JavaFeature which introduced a given keyword; null if the supplied string is not a soft keyword 
   */
  public static @Nullable JavaFeature softKeywordFeature(@NotNull CharSequence keyword) {
    return SOFT_KEYWORDS.get(keyword);
  }
}
