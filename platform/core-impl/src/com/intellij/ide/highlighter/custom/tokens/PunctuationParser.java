/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ide.highlighter.custom.tokens;

import com.intellij.psi.CustomHighlighterTokenType;

/**
 * @author peter
 */
public class PunctuationParser extends TokenParser {
  @Override
  public boolean hasToken(int position) {
    final char c = myBuffer.charAt(position);
    if (".,:;".indexOf(c) >= 0) {
      myTokenInfo.updateData(position, position+1, CustomHighlighterTokenType.PUNCTUATION);
      return true;
    }
    return false;
  }
}
