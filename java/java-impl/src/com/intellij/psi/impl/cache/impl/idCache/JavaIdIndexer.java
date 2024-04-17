// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lang.java.JavaParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.id.LexerBasedIdIndexer;
import org.jetbrains.annotations.NotNull;

public final class JavaIdIndexer extends LexerBasedIdIndexer {
  @Override
  public @NotNull Lexer createLexer(final @NotNull OccurrenceConsumer consumer) {
    return createIndexingLexer(consumer);
  }

  public static Lexer createIndexingLexer(OccurrenceConsumer consumer) {
    Lexer javaLexer = JavaParserDefinition.createLexer(LanguageLevel.JDK_1_3);
    return new JavaFilterLexer(javaLexer, consumer);
  }
}
