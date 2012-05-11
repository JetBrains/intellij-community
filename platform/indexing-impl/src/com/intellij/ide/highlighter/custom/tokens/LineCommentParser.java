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
 * @author dsl
 */
public class LineCommentParser extends PrefixedTokenParser {
  private final boolean myAtStartOnly;

  public LineCommentParser(String prefix, boolean atStartOnly) {
    super(prefix, CustomHighlighterTokenType.LINE_COMMENT);
    myAtStartOnly = atStartOnly;
  }

  @Override
  public boolean hasToken(int position) {
    if (myAtStartOnly && position > 0 && myBuffer.charAt(position - 1) != '\n') {
      return false;
    }

    return super.hasToken(position);
  }

  protected int getTokenEnd(int position) {
    for (; position < myEndOffset; position++) {
      if (myBuffer.charAt(position) == '\n') break;
    }
    return position;
  }

}
