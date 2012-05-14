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
import com.intellij.psi.tree.IElementType;

import java.util.Arrays;
import java.util.List;

/**
 * @author Maxim.Mossienko
 */
public class BraceTokenParser extends PrefixedTokenParser {

  public BraceTokenParser(String prefix, IElementType tokenType) {
    super(prefix, tokenType);
  }

  //these getters here can't be replaced with constant fields because each token parser remembers the buffer
  // which in DocumentImpl.getCharSequence() maintains a reference to the document and thus many
  // things will be leaked
  public static List<BraceTokenParser> getBraces() {
    return Arrays.asList(new BraceTokenParser("{", CustomHighlighterTokenType.L_BRACE),
                         new BraceTokenParser("}", CustomHighlighterTokenType.R_BRACE));
  }

  public static List<BraceTokenParser> getParens() {
    return Arrays.asList(new BraceTokenParser("(", CustomHighlighterTokenType.L_PARENTH),
                         new BraceTokenParser(")", CustomHighlighterTokenType.R_PARENTH));
  }

  public static List<BraceTokenParser> getBrackets() {
    return Arrays.asList(new BraceTokenParser("[", CustomHighlighterTokenType.L_BRACKET),
                         new BraceTokenParser("]", CustomHighlighterTokenType.R_BRACKET));
  }

  public static List<BraceTokenParser> getAngleBrackets() {
    return Arrays.asList(new BraceTokenParser("<", CustomHighlighterTokenType.L_ANGLE),
                         new BraceTokenParser(">", CustomHighlighterTokenType.R_ANGLE));
  }

  protected int getTokenEnd(int position) {
    return position;
  }
}
