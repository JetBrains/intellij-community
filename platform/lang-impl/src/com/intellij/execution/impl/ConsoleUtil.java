/*
 * Copyright 2000-2011 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.impl;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.intellij.execution.impl.ConsoleViewImpl.HyperlinkTokenInfo;
import static com.intellij.execution.impl.ConsoleViewImpl.TokenInfo;

/**
 * Holds utility methods for console processing.
 * 
 * @author Denis Zhdanov
 * @since 4/6/11 3:50 PM
 */
public class ConsoleUtil {

  private ConsoleUtil() {
  }

  public static void addToken(int length, @Nullable HyperlinkInfo info, ConsoleViewContentType contentType, @NotNull List<TokenInfo> tokens) {
    int startOffset = 0;
    if (!tokens.isEmpty()) {
      final TokenInfo lastToken = tokens.get(tokens.size() - 1);
      if (lastToken.contentType == contentType && info == lastToken.getHyperlinkInfo()) {
        lastToken.endOffset += length; // optimization
        return;
      }
      else {
        startOffset = lastToken.endOffset;
      }
    }

    tokens.add(info != null ? new HyperlinkTokenInfo(contentType, startOffset, startOffset + length, info)
                            : new TokenInfo(contentType, startOffset, startOffset + length));
  }

  /**
   * Utility method that allows to adjust ranges of the given tokens within the text removal.
   * <p/>
   * <b>Note:</b> it's assumed that the given tokens list is ordered by offset in ascending order.
   * 
   * @param tokens       target tokens ordered by offset in ascending order
   * @param startOffset  start offset of the removed text (inclusive)
   * @param endOffset    end offset of the removed text (exclusive)
   */
  public static void updateTokensOnTextRemoval(@NotNull List<? extends TokenInfo> tokens, int startOffset, int endOffset) {
    final int firstIndex = findTokenInfoIndexByOffset(tokens, startOffset);
    if (firstIndex >= tokens.size()) {
      return;
    }
    
    int removedSymbolsNumber = endOffset - startOffset;
    boolean updateOnly = false;
    int removeIndexStart = -1;
    int removeIndexEnd = -1;
    final TokenInfo firstToken = tokens.get(firstIndex);

    if (startOffset == firstToken.startOffset) {
      // Removed range is located entirely at the first token.
      if (endOffset < firstToken.endOffset) {
        firstToken.endOffset -= removedSymbolsNumber;
        updateOnly = true;
      }
      // The first token is completely removed.
      else {
        removeIndexStart = removeIndexEnd = firstIndex;
      }
    }
    // Removed range is located entirely at the first token. 
    else if (endOffset <= firstToken.endOffset) {
      firstToken.endOffset -= removedSymbolsNumber;
      updateOnly = true;
    }

    for (int i = firstIndex + 1; i < tokens.size(); i++) {
      final TokenInfo tokenInfo = tokens.get(i);
      if (updateOnly) {
        tokenInfo.startOffset -= removedSymbolsNumber;
        tokenInfo.endOffset -= removedSymbolsNumber;
        continue;
      }

      // We know that start offset lays before the current token, so, it's completely removed if end offset is not less than
      // the token's end offset.
      if (endOffset >= tokenInfo.endOffset) {
        if (removeIndexStart < 0) {
          removeIndexStart = i;
        }
        removeIndexEnd = i;
        continue;
      }
      
      // Update current token offsets and adjust ranges of all subsequent tokens.
      tokenInfo.startOffset = startOffset;
      tokenInfo.endOffset = startOffset + (tokenInfo.endOffset - endOffset);
      updateOnly = true;
    }

    if (removeIndexStart >= 0) {
      tokens.subList(removeIndexStart, removeIndexEnd + 1).clear();
    } 
  }

  /**
   * Searches given collection for the token that contains given offset.
   * <p/>
   * <b>Note:</b> it's assumed that the given tokens list is ordered by offset in ascending order.
   * 
   * @param tokens  target tokens ordered by offset in ascending order
   * @param offset  target offset
   * @return        index of the target token within the given list; given list length if no such token is found
   */
  public static int findTokenInfoIndexByOffset(@NotNull List<? extends TokenInfo> tokens, final int offset) {
    int low = 0;
    int high = tokens.size() - 1;

    while (low <= high) {
      final int mid = (low + high) / 2;
      final TokenInfo midVal = tokens.get(mid);
      if (offset < midVal.startOffset) {
        high = mid - 1;
      }
      else if (offset >= midVal.endOffset) {
        low = mid + 1;
      }
      else {
        return mid;
      }
    }
    return tokens.size();
  }
}
