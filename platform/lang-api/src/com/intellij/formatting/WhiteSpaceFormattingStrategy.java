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
package com.intellij.formatting;

import org.jetbrains.annotations.NotNull;

/**
 * Defines common contract for strategy that determines if particular symbol or sequence of symbols may be treated as
 * white space during formatting.
 * <p/>
 * <code>'Treated as white space'</code> here means that formatter is free to remove any number of such symbols or replace them
 * by 'pure' white spaces.
 *
 * @author Denis Zhdanov
 * @since Sep 20, 2010 5:05:08 PM
 */
public interface WhiteSpaceFormattingStrategy {

  /**
   * Checks if given sub-sequence of the given text contains symbols that may be treated as white spaces.
   *
   * @param text      text to check
   * @param start     start offset to use with the given text (inclusive)
   * @param end       end offset to use with the given text (exclusive)
   * @return          offset of the first symbol that belongs to <code>[startOffset; endOffset)</code> range
   *                  and is not treated as white space by the current strategy <b>or</b> value that is greater
   *                  or equal to the given <code>'end'</code> parameter if all target sub-sequence symbols
   *                  can be treated as white spaces
   */
  int check(@NotNull CharSequence text, int start, int end);

  /**
   * @return    <code>true</code> if default white space strategy used by formatter should be replaced by the current one;
   *            <code>false</code> to indicate that current strategy should be used in composition with default strategy
   *            if any, i.e. particular symbols sequence should be considered as white spaces if any of composed
   *            strategies defines so
   */
  boolean replaceDefaultStrategy();
}
