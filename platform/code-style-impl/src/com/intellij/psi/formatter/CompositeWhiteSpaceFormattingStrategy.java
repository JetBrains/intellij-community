// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

/**
 * Allows to combine multiple {@link WhiteSpaceFormattingStrategy} implementations.
 * <p/>
 * Not thread-safe.
 */
public final class CompositeWhiteSpaceFormattingStrategy implements WhiteSpaceFormattingStrategy {

  private final List<WhiteSpaceFormattingStrategy> myStrategies;
  private final boolean myReplaceDefaultStrategy;

  public CompositeWhiteSpaceFormattingStrategy(boolean replaceDefaultStrategy,
                                               @NotNull Collection<? extends WhiteSpaceFormattingStrategy> strategies) {
    myStrategies = List.copyOf(strategies);
    myReplaceDefaultStrategy = replaceDefaultStrategy;
  }

  public CompositeWhiteSpaceFormattingStrategy(@NotNull Collection<? extends WhiteSpaceFormattingStrategy> strategies) {
    this(false, strategies);
  }

  @Override
  public int check(@NotNull CharSequence text, int start, int end) {
    int offset = start;
    while (offset < end) {
      int oldOffset = offset;
      for (WhiteSpaceFormattingStrategy strategy : myStrategies) {
        offset = strategy.check(text, offset, end);
        if (offset > oldOffset) {
          break;
        }
      }
      if (offset == oldOffset) {
        return offset;
      }
    }
    return offset;
  }

  @Override
  public boolean replaceDefaultStrategy() {
    return myReplaceDefaultStrategy;
  }

  @Override
  public @NotNull CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                           @NotNull CharSequence text,
                                                           int startOffset,
                                                           int endOffset, CodeStyleSettings codeStyleSettings, ASTNode nodeAfter)
  {
    CharSequence result = whiteSpaceText;
    for (WhiteSpaceFormattingStrategy strategy : myStrategies) {
      result = strategy.adjustWhiteSpaceIfNecessary(result, text, startOffset, endOffset, codeStyleSettings, nodeAfter);
    }
    return result;
  }

  @Override
  public CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                  @NotNull PsiElement startElement,
                                                  int startOffset,
                                                  int endOffset, CodeStyleSettings codeStyleSettings)
  {
    CharSequence result = whiteSpaceText;
    for (WhiteSpaceFormattingStrategy strategy : myStrategies) {
      result = strategy.adjustWhiteSpaceIfNecessary(result, startElement, startOffset, endOffset, codeStyleSettings);
    }
    return result;
  }

  @Override
  public boolean containsWhitespacesOnly(@NotNull ASTNode node) {
    for (WhiteSpaceFormattingStrategy strategy : myStrategies) {
      if (strategy.containsWhitespacesOnly(node)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean addWhitespace(@NotNull ASTNode treePrev, @NotNull LeafElement whiteSpaceElement) {
    return false;
  }
}
