// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl.cache.impl;

import com.intellij.lexer.Lexer;
import org.jetbrains.annotations.NotNull;

public interface IdAndToDoScannerBasedOnFilterLexer {
  // lexer should be the same
  @NotNull
  Lexer createLexer(@NotNull OccurrenceConsumer consumer);
}
