// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.formatter;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.codeStyle.CodeStyleSettings;
import com.intellij.psi.impl.source.tree.LeafElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Abstract common {@link WhiteSpaceFormattingStrategy} implementation that doesn't replace default strategy and doesn't
 * adjust white space and
 */
public abstract class AbstractWhiteSpaceFormattingStrategy implements WhiteSpaceFormattingStrategy {

  @Override
  public boolean replaceDefaultStrategy() {
    return false;
  }

  @Override
  public @NotNull CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                           @NotNull CharSequence text,
                                                           int startOffset,
                                                           int endOffset, CodeStyleSettings codeStyleSettings, ASTNode nodeAfter)
  {
    // Does nothing
    return whiteSpaceText;
  }

  @Override
  public CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText,
                                                  @NotNull PsiElement startElement,
                                                  final int startOffset,
                                                  final int endOffset, CodeStyleSettings codeStyleSettings)
  {
    assert startElement.getTextRange().contains(startOffset)
      : String.format("Element: %s, range: %s, offset: %d", startElement, startElement.getTextRange(), startOffset); 
    
    // Collect target text from the PSI elements and delegate to the text-based method.
    StringBuilder buffer = new StringBuilder();
    for (PsiElement current = startElement; current != null && current.getTextRange().getStartOffset() < endOffset; current = next(current)) {
      final TextRange range = current.getTextRange();
      final String text = current.getText();
      if (StringUtil.isEmpty(text)) {
        continue;
      }
      
      int start = startOffset > range.getStartOffset() ? startOffset - range.getStartOffset() : 0;
      if (start >= text.length()) {
        continue;
      }

      int end = endOffset < range.getEndOffset() ? text.length() - (range.getEndOffset() - endOffset) : text.length();
      if (end <= start) {
        continue;
      }

      if (start == 0 && end == text.length()) {
        buffer.append(text);
      }
      else {
        buffer.append(text, start, end);
      } 
    }
    
    return adjustWhiteSpaceIfNecessary(whiteSpaceText, buffer, 0, endOffset - startOffset, codeStyleSettings, null);
  }

  private static @Nullable PsiElement next(final @NotNull PsiElement element) {
    for (PsiElement anchor = element; anchor != null; anchor = anchor.getParent()) {
      final PsiElement result = element.getNextSibling();
      if (result != null) {
        return result;
      }
    }
    return null;
  }

  @Override
  public boolean containsWhitespacesOnly(@NotNull ASTNode node) {
    return false;
  }

  @Override
  public boolean addWhitespace(@NotNull ASTNode treePrev, @NotNull LeafElement whiteSpaceElement) {
    return false;
  }
}
