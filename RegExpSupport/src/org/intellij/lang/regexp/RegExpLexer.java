/*
 * Copyright 2006 Sascha Weinreuter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.intellij.lang.regexp;

import com.intellij.lexer.FlexAdapter;
import com.intellij.lexer.Lexer;
import com.intellij.lexer.LookAheadLexer;
import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

import java.util.EnumSet;

public class RegExpLexer extends LookAheadLexer {

  public RegExpLexer(EnumSet<RegExpCapability> capabilities) {
    super(new FlexRegExpLexer(capabilities));
  }

  @Override
  protected void lookAhead(@NotNull Lexer baseLexer) {
    final IElementType tokenType = baseLexer.getTokenType();
    if (!RegExpTT.CHARACTERS.contains(tokenType) && tokenType != RegExpTT.RBRACE) {
      advanceLexer(baseLexer);
      if (baseLexer.getTokenType() == RegExpTT.MINUS) {
        advanceAs(baseLexer, RegExpTT.CHARACTER);
      }
    }
    else {
      super.lookAhead(baseLexer);
    }
  }

  static class FlexRegExpLexer extends FlexAdapter {

    private static final int COMMENT_MODE = 1 << 14;
    private static final int NESTED_STATES = 1 << 15;
    private static final int CAPTURING_GROUPS = 1 << 16;
    private final EnumSet<RegExpCapability> myCapabilities;

    FlexRegExpLexer(EnumSet<RegExpCapability> capabilities) {
      super(new _RegExLexer(capabilities));
      myCapabilities = capabilities;
    }

    @Override
    public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
      getFlex().commentMode = (initialState & COMMENT_MODE) != 0 || myCapabilities.contains(RegExpCapability.COMMENT_MODE);
      super.start(buffer, startOffset, endOffset, initialState & ~COMMENT_MODE);
    }

    @Override
    public _RegExLexer getFlex() {
      return (_RegExLexer)super.getFlex();
    }

    @Override
    public int getState() {
      final _RegExLexer flex = getFlex();
      int state = super.getState();
      if (flex.commentMode) {
        state |= COMMENT_MODE;
      }
      if (!flex.states.isEmpty()) {
        state |= NESTED_STATES;
      }
      if (flex.capturingGroupCount != 0) {
        state |= CAPTURING_GROUPS;
      }
      return state;
    }
  }
}
