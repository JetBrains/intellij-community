// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.idCache;

import com.intellij.lexer.Lexer;
import com.intellij.psi.impl.cache.impl.IdAndToDoScannerBasedOnFilterLexer;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.todo.LexerBasedTodoIndexer;
import org.jetbrains.annotations.NotNull;

public final class JavaTodoIndexer extends LexerBasedTodoIndexer implements IdAndToDoScannerBasedOnFilterLexer {
  @Override
  public @NotNull Lexer createLexer(@NotNull OccurrenceConsumer consumer) {
    return JavaIdIndexer.createIndexingLexer(consumer);
  }
}
