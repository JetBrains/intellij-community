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

import static com.intellij.execution.impl.ConsoleViewImpl.TokenInfo;
import static com.intellij.execution.impl.ConsoleViewImpl.HyperlinkTokenInfo;

import com.intellij.execution.filters.HyperlinkInfo;
import com.intellij.execution.ui.ConsoleViewContentType;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Holds utility methods for console processing.
 * 
 * @author Denis Zhdanov
 * @since 4/6/11 3:50 PM
 */
public class ConsoleUtil {

  private ConsoleUtil() {
  }

  public static void addToken(int length, @Nullable HyperlinkInfo info, ConsoleViewContentType contentType, List<TokenInfo> tokens) {
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
}
