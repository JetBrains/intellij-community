// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.spellchecker;

import com.intellij.psi.PsiIdentifier;
import com.intellij.spellchecker.inspections.IdentifierSplitter;
import com.intellij.spellchecker.tokenizer.TokenConsumer;
import com.intellij.spellchecker.tokenizer.Tokenizer;
import org.jetbrains.annotations.NotNull;


public class PsiIdentifierTokenizer extends Tokenizer<PsiIdentifier> {
  @Override
  public void tokenize(@NotNull PsiIdentifier element, TokenConsumer consumer) {
    consumer.consumeToken(element, true, IdentifierSplitter.getInstance());
  }
}
