/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.openapi.editor;

import gnu.trove.TIntHashSet;
import org.jetbrains.annotations.NotNull;

/**
 * Default {@link LineWrapPositionStrategy} implementation. Is assumed to provide language-agnostic algorithm that may
 * be used with almost any kind of text.
 *
 * @author Denis Zhdanov
 * @since Aug 25, 2010 11:33:00 AM
 */
public class DefaultLineWrapPositionStrategy implements LineWrapPositionStrategy {

  /** Contains white space characters (has special treatment during soft wrap position calculation). */
  private static final TIntHashSet WHITE_SPACES = new TIntHashSet();
  static {
    WHITE_SPACES.add(' ');
    WHITE_SPACES.add('\t');
  }

  /**
   * Contains symbols that are special in that soft wrap is allowed to be performed only
   * after them (not before).
   */
  private static final TIntHashSet SPECIAL_SYMBOLS_TO_WRAP_AFTER = new TIntHashSet();
  static {
    SPECIAL_SYMBOLS_TO_WRAP_AFTER.add(',');
    SPECIAL_SYMBOLS_TO_WRAP_AFTER.add(';');
    SPECIAL_SYMBOLS_TO_WRAP_AFTER.add(')');
  }

  /**
   * Contains symbols that are special in that soft wrap is allowed to be performed only
   * before them (not after).
   */
  private static final TIntHashSet SPECIAL_SYMBOLS_TO_WRAP_BEFORE = new TIntHashSet();
  static {
    SPECIAL_SYMBOLS_TO_WRAP_BEFORE.add('(');
    SPECIAL_SYMBOLS_TO_WRAP_BEFORE.add('.');
  }

  @Override
  public int calculateWrapPosition(@NotNull CharSequence text,
                                   int startOffset,
                                   int endOffset,
                                   final int maxPreferredOffset,
                                   boolean allowToBeyondMaxPreferredOffset)
  {
    if (endOffset <= startOffset) {
      return endOffset;
    }

    int maxPreferredOffsetToUse = maxPreferredOffset >= endOffset ? endOffset - 1 : maxPreferredOffset;
    // Try to find target offset that is not greater than preferred position.
    for (int i = maxPreferredOffsetToUse; i > startOffset; i--) {
      char c = text.charAt(i);

      if (WHITE_SPACES.contains(c)) {
        return i < maxPreferredOffsetToUse ? i + 1 : i;
      }

      // Don't wrap on the non-id symbol preceded by another non-id symbol. E.g. consider that we have a statement
      // like 'foo(int... args)'. We don't want to wrap on the second or third dots then.
      if (i > startOffset + 1 && !isIdSymbol(c) && !isIdSymbol(text.charAt(i - 1))) {
        continue;
      }
      if (SPECIAL_SYMBOLS_TO_WRAP_AFTER.contains(c)) {
        if (i < maxPreferredOffsetToUse) {
          return i + 1;
        }
        continue;
      }
      if (SPECIAL_SYMBOLS_TO_WRAP_BEFORE.contains(c) || WHITE_SPACES.contains(c)) {
        return i;
      }

      // Don't wrap on a non-id symbol followed by non-id symbol, e.g. don't wrap between two pluses at i++.
      // Also don't wrap before non-id symbol preceded by a space - wrap on space instead;
      if (!isIdSymbol(c) && (i < startOffset + 2 || (isIdSymbol(text.charAt(i - 1)) && !WHITE_SPACES.contains(text.charAt(i - 1))))) {
        return i;
      }
    }

    // Try to find target offset that is greater than preferred position.
    for (int i = maxPreferredOffsetToUse + 1; i < endOffset; i++) {
      char c = text.charAt(i);
      if (WHITE_SPACES.contains(c)) {
        return i;
      }
      // Don't wrap on the non-id symbol preceded by another non-id symbol. E.g. consider that we have a statement
      // like 'foo(int... args)'. We don't want to wrap on the second or third dots then.
      if (i < endOffset - 1 && !isIdSymbol(c) && !isIdSymbol(text.charAt(i + 1)) && !isIdSymbol(text.charAt(i - 1))) {
        continue;
      }
      if (SPECIAL_SYMBOLS_TO_WRAP_BEFORE.contains(c)) {
        return i;
      }
      if (SPECIAL_SYMBOLS_TO_WRAP_AFTER.contains(c) && i < endOffset - 1) {
        return i + 1;
      }

      // Don't wrap on a non-id symbol followed by non-id symbol, e.g. don't wrap between two pluses at i++;
      if (!isIdSymbol(c) && (i >= endOffset - 1 || isIdSymbol(text.charAt(i + 1)))) {
        return i;
      }
    }
    return maxPreferredOffset;
  }

  private static boolean isIdSymbol(char c) {
    return c == '_' || c == '$' || (c >= '0' && c <= '9') || (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
  }
}
