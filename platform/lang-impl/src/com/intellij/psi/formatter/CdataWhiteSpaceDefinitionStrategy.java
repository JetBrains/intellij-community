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
package com.intellij.psi.formatter;

import com.intellij.formatting.WhiteSpaceFormattingStrategy;
import com.intellij.util.text.CharArrayUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * {@link WhiteSpaceFormattingStrategy} implementation that considers to be white spaces all symbols from
 * standard XML CDATA section ('{@code <![CDATA[...]]>}').
 * <p/>
 * Thread-safe.
 *
 * @author Denis Zhdanov
 * @since Sep 20, 2010 5:39:50 PM
 */
public class CdataWhiteSpaceDefinitionStrategy implements WhiteSpaceFormattingStrategy {

  @NonNls public static final String CDATA_START = "<![CDATA[";
  @NonNls public static final String CDATA_END = "]]>";

  @Override
  public int check(@NotNull CharSequence text, int start, int end) {
    if (CharArrayUtil.indexOf(text, CDATA_START, start, end) != start) {
      return start;
    }

    int i = CharArrayUtil.indexOf(text, CDATA_END, start, end);
    if (i < 0) {
      return start;
    }

    int result = i + CDATA_END.length();
    return result > end ? start : result;
  }
}
