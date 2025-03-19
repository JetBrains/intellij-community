// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.NotNull;

public class WhiteSpaceFormattingStrategyAdapter implements WhiteSpaceFormattingStrategy {
  
  private final WhiteSpaceFormattingStrategy DELEGATE = WhiteSpaceFormattingStrategyFactory.DEFAULT_STRATEGY;
  
  @Override
  public int check(@NotNull CharSequence text, int start, int end) {
    return DELEGATE.check(text, start, end);
  }

  @Override
  public boolean containsWhitespacesOnly(@NotNull ASTNode node) {
    return false;
  }

  @Override
  public boolean replaceDefaultStrategy() {
    return false;
  }

  @Override
  public @NotNull CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                           @NotNull CharSequence text,
                                                           int startOffset,
                                                           int endOffset, CodeStyleSettings codeStyleSettings, ASTNode nodeAfter) {
    return whiteSpaceText;
  }

  @Override
  public CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                  @NotNull PsiElement startElement,
                                                  int startOffset,
                                                  int endOffset, CodeStyleSettings codeStyleSettings) {
    return whiteSpaceText;
  }

  @Override
  public boolean addWhitespace(@NotNull ASTNode treePrev, @NotNull LeafElement whiteSpaceElement) {
    return false;
  }
}
