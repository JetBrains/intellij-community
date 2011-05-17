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

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.NotNull;

/**
 * Defines common contract for strategy that determines if particular symbol or sequence of symbols may be treated as
 * white space during formatting.
 * <p/>
 * <code>'Treated as white space'</code> here means that formatter may remove such symbols or replace them to other
 * 'white space symbols' if necessary.
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

  /**
   * Main formatter duty is to tweak white space symbols (add/remove/modify them). However, it may be necessary
   * to pay special attention to that. For example it may be necessary to ensure that <code>'\'</code> symbol is
   * used inside multiline expression in case of Python etc.
   * <p/>
   * This method defines a callback that allows to modify white space symbols to use for replacing particular
   * document symbols sub-sequence if necessary.
   *
   * @param whiteSpaceText    white space text to use by default for replacing sub-sequence of the given text
   * @param text              target text which region is to be replaced by the given white space symbols
   * @param startOffset       start offset to use with the given text (inclusive)
   * @param endOffset         end offset to use with the given text (exclusive)
   * @return                  symbols to use for replacing <code>[startOffset; endOffset)</code> sub-sequence of the given text
   */
  @NotNull
  CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText, @NotNull CharSequence text, int startOffset,
                                           int endOffset);

            
  /**
   * PSI-based version of {@link #adjustWhiteSpaceIfNecessary(CharSequence, CharSequence, int, int)}.
   * <p/>
   * There is a possible case that particular changes are performed to PSI tree and it's not yet synchronized with the underlying
   * document. Hence, we can't directly work with document char sequence but need to traverse PSI tree instead. I.e. we start with
   * particular PSI element that contains given start offset and process its right siblings/relatives until given end offset
   * is reached.
   * 
   * @param whiteSpaceText    white space text to use by default for replacing sub-sequence of the given text
   * @param startElement      PSI element that contains given start offset
   * @param startOffset       start offset to use with the given text (inclusive)
   * @param endOffset         end offset to use with the given text (exclusive)
   * @return                  symbols to use for replacing <code>[startOffset; endOffset)</code> sub-sequence of the given text
   */
  CharSequence adjustWhiteSpaceIfNecessary(@NotNull CharSequence whiteSpaceText, @NotNull PsiElement startElement, int startOffset,
                                           int endOffset);
}
