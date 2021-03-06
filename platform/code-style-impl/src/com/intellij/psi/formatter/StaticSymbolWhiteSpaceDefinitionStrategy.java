// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.formatter;

import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.NotNull;

/**
 * {@link WhiteSpaceFormattingStrategy} implementation that is pre-configured with the set of symbols that may
 * be treated as white spaces.
 * <p/>
 * Please note that this class exists just for performance reasons (functionally we can use
 * {@link StaticTextWhiteSpaceDefinitionStrategy} with strings consisting from single symbol all the time).
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 */
public class StaticSymbolWhiteSpaceDefinitionStrategy extends AbstractWhiteSpaceFormattingStrategy {
  private final IntSet myWhiteSpaceSymbols = new IntOpenHashSet();

  /**
   * Creates new {@code StaticWhiteSpaceDefinitionStrategy} object with the symbols that should be treated as white spaces.
   *
   * @param whiteSpaceSymbols   symbols that should be treated as white spaces by the current strategy
   */
  public StaticSymbolWhiteSpaceDefinitionStrategy(char ... whiteSpaceSymbols) {
    for (char symbol : whiteSpaceSymbols) {
      myWhiteSpaceSymbols.add(symbol);
    }
  }

  @Override
  public int check(@NotNull CharSequence text, int start, int end) {
    for (int i = start; i < end; i++) {
      char c = text.charAt(i);
      if (!myWhiteSpaceSymbols.contains(c)) {
        return i;
      }
    }
    return end;
  }
}
