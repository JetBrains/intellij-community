// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.java.lexer;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.source.tree.JavaDocElementTypeFactory;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Set;

import static com.intellij.psi.PsiKeyword.*;

public final class JavaLexer extends BasicJavaLexer {

  private static final Set<String> KEYWORDS = ContainerUtil.immutableSet(
    ABSTRACT, BOOLEAN, BREAK, BYTE, CASE, CATCH, CHAR, CLASS, CONST, CONTINUE, DEFAULT, DO, DOUBLE, ELSE, EXTENDS, FINAL, FINALLY,
    FLOAT, FOR, GOTO, IF, IMPLEMENTS, IMPORT, INSTANCEOF, INT, INTERFACE, LONG, NATIVE, NEW, PACKAGE, PRIVATE, PROTECTED, PUBLIC,
    RETURN, SHORT, STATIC, STRICTFP, SUPER, SWITCH, SYNCHRONIZED, THIS, THROW, THROWS, TRANSIENT, TRY, VOID, VOLATILE, WHILE,
    TRUE, FALSE, NULL, NON_SEALED);

  private static final Set<CharSequence> JAVA9_KEYWORDS = CollectionFactory.createCharSequenceSet(
    Arrays.asList(OPEN, MODULE, REQUIRES, EXPORTS, OPENS, USES, PROVIDES, TRANSITIVE, TO, WITH));

  public JavaLexer(@NotNull LanguageLevel level) {
    super(level, JavaDocElementTypeFactory.INSTANCE);
  }
  public static boolean isKeyword(@NotNull String id, @NotNull LanguageLevel level) {
    return KEYWORDS.contains(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_4) && ASSERT.equals(id) ||
           level.isAtLeast(LanguageLevel.JDK_1_5) && ENUM.equals(id);
  }

  public static boolean isSoftKeyword(@NotNull CharSequence id, @NotNull LanguageLevel level) {
    return level.isAtLeast(LanguageLevel.JDK_1_9) && JAVA9_KEYWORDS.contains(id) ||
           level.isAtLeast(LanguageLevel.JDK_10) && VAR.contentEquals(id) ||
           level.isAtLeast(LanguageLevel.JDK_16) && RECORD.contentEquals(id) ||
           level.isAtLeast(LanguageLevel.JDK_14) && YIELD.contentEquals(id) ||
           level.isAtLeast(LanguageLevel.JDK_17) && (SEALED.contentEquals(id) || PERMITS.contentEquals(id)) ||
           level.isAtLeast(LanguageLevel.JDK_20_PREVIEW) && WHEN.contentEquals(id);
  }

  @Override
  public IElementType getTokenType() {
    return super.getTokenType();
  }

  @Override
  public int getTokenStart() {
    return super.getTokenStart();
  }

  @Override
  public int getTokenEnd() {
    return super.getTokenEnd();
  }
}
