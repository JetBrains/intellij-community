// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.impl;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.ui.ConsoleViewContentType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * Stores list of tokens (a token is {@link TokenInfo} which is a text plus {@link ConsoleViewContentType} plus {@link HyperlinkInfo}).
 * Tries to maintain the total token text length not more than {@link #maxCapacity}, trims tokens from the beginning on overflow.
 * Not thread-safe.
 *
 * Add token via {@link #print(String, ConsoleViewContentType, HyperlinkInfo)}
 * Get all tokens via {@link #drain()}
 */
@ApiStatus.Internal
public final class TokenBuffer {
  // special token which means that the deferred text starts with "\r" so it shouldn't be appended to the document end.
  // Instead, the last line of the document should be removed
  public static final TokenInfo CR_TOKEN = new TokenInfo(ConsoleViewContentType.SYSTEM_OUTPUT, "\r", null);
  private final int maxCapacity;  // if size becomes > maxCapacity we should trim tokens from the beginning
  private final Deque<TokenInfo> tokens = new ArrayDeque<>(10); // each call to print() is stored here
  private int size; // total lengths of all tokens
  private int startIndex; // index of text start in the first TokeInfo. This TokenInfo can become sliced after total size overflows maxCapacity

  @ApiStatus.Internal
  public TokenBuffer(int maxCapacity) {
    this.maxCapacity = maxCapacity;
    if (maxCapacity <= 0) throw new IllegalArgumentException(String.valueOf(maxCapacity));
  }

  public void print(@NotNull String text, @NotNull ConsoleViewContentType contentType, @Nullable HyperlinkInfo info) {
    int start = 0;
    while (start < text.length()) {
      if (hasTrailingCR()) {
        combineTrailingCRWith(text);
      }
      int crIndex = text.indexOf('\r', start);
      if (crIndex == -1 || crIndex == text.length() - 1) {
        TokenInfo tokenInfo = new TokenInfo(contentType, text.substring(start), info);
        tokens.addLast(tokenInfo);
        size += tokenInfo.length();
        break;
      }

      if (start!=crIndex) {
        TokenInfo tokenInfo = new TokenInfo(contentType, text.substring(start, crIndex), info);
        tokens.addLast(tokenInfo);
        size += tokenInfo.length();
      }
      removeLastLine();
      // text[start..crIndex) should be removed
      start = crIndex + 1;
    }
    trim();
  }

  // has to combine "\r" from the previous token with "\n" from current token to make it LF
  private boolean hasTrailingCR() {
    return !tokens.isEmpty() && tokens.peekLast() != CR_TOKEN && StringUtil.endsWithChar(tokens.peekLast().getText(), '\r');
  }

  // \r with \n should be \n
  // \r with other character c should be remove last line, c
  private void combineTrailingCRWith(@NotNull String currentText) {
    if (StringUtil.startsWith(currentText, "\n")) {
      TokenInfo last = tokens.removeLast();
      String lastTextWithNoCR = last.getText().substring(0, last.length() - 1);
      if (!lastTextWithNoCR.isEmpty()) {
        TokenInfo newLast = new TokenInfo(last.contentType, lastTextWithNoCR, last.getHyperlinkInfo());
        tokens.addLast(newLast);
        size --;
      }
      return;
    }
    removeLastLine();
  }

  private void removeLastLine() {
    // when \r happens, need to delete the last line
    while (!tokens.isEmpty() && tokens.peekLast() != CR_TOKEN) {
      TokenInfo last = tokens.removeLast();
      String text = last.getText();
      int lfIndex = text.lastIndexOf('\n');
      if (lfIndex != -1) {
        // split token
        TokenInfo newToken = new TokenInfo(last.contentType, text.substring(0, lfIndex + 1), last.getHyperlinkInfo());
        tokens.addLast(newToken);
        size -= text.length() - newToken.length();
        return;
      }
      // remove the token entirely, move to the previous
      size -= text.length();
    }
    if (tokens.isEmpty()) {
      // \r at the very beginning. return CR_TOKEN to signal this
      tokens.addLast(CR_TOKEN);
      size ++;
    }
  }

  private void trim() {
    // toss tokens from the beginning until size became < maxCapacity
    int excess = size - maxCapacity;
    while (excess > startIndex) {
      TokenInfo info = tokens.getFirst();
      int length = info.length();
      if (length > excess) {
        // slice a part of this info
        startIndex = excess;
        break;
      }
      startIndex = 0;
      tokens.removeFirst();
      size -= info.length();
      excess = size - maxCapacity;
    }
    assert startIndex >= 0 && size >= 0 && maxCapacity >= 0: "startIndex="+startIndex+"; size="+size+"; maxCapacity="+maxCapacity;
    //assert tokens.toList().stream().mapToInt(TokenInfo::length).sum() == size;
  }

  @ApiStatus.Internal
  public int length() {
    return size - startIndex;
  }

  @ApiStatus.Internal
  public void clear() {
    tokens.clear();
    startIndex = 0;
    size = 0;
  }

  @ApiStatus.Internal
  public static @NotNull CharSequence getRawText(@NotNull List<? extends TokenInfo> tokens) {
    int size = 0;
    for (TokenInfo token : tokens) {
      size += token.getText().length();
    }
    StringBuilder result = new StringBuilder(size);
    for (TokenInfo token : tokens) {
      result.append(token.getText());
    }
    return result.toString();
  }

  // the first token may be CR_TOKEN meaning that instead of appending it we should delete the last line of the document
  // all the remaining text is guaranteed not to contain CR_TOKEN - they can be appended safely to the document end
  @NotNull
  public List<TokenInfo> drain() {
    if (hasTrailingCR()) {
      removeLastLine();
    }
    try {
      return getInfos();
    }
    finally {
      clear();
    }
  }

  private @NotNull List<TokenInfo> getInfos() {
    List<TokenInfo> list = tokens.isEmpty() ? Collections.emptyList() : new ArrayList<>(tokens);
    if (startIndex != 0) {
      // slice the first token
      TokenInfo first = list.get(0);
      TokenInfo sliced = new TokenInfo(first.contentType, first.getText().substring(startIndex), first.getHyperlinkInfo());
      return ContainerUtil.concat(Collections.singletonList(sliced), list.subList(1, list.size()));
    }
    return list;
  }

  public int getCycleBufferSize() {
    return maxCapacity;
  }

  @ApiStatus.Internal
  public static final class TokenInfo {
    public final @NotNull ConsoleViewContentType contentType;
    private final String text;
    private final HyperlinkInfo myHyperlinkInfo;

    public TokenInfo(@NotNull ConsoleViewContentType contentType,
              @NotNull String text,
              @Nullable HyperlinkInfo hyperlinkInfo) {
      this.contentType = contentType;
      myHyperlinkInfo = hyperlinkInfo;
      this.text = text;
    }

    @ApiStatus.Internal
    public int length() {
      return text.length();
    }

    @Override
    public String toString() {
      return contentType + "[" + length() + "]";
    }

    @ApiStatus.Internal
    public HyperlinkInfo getHyperlinkInfo() {
      return myHyperlinkInfo;
    }

    @NotNull
    @ApiStatus.Internal
    public String getText() {
      return text;
    }
  }

  @Override
  public String toString() {
    return getRawText(getInfos()).toString();
  }
}
