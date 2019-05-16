/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.intellij.plugins.markdown.lang.lexer;

import com.intellij.lexer.LexerBase;
import com.intellij.psi.tree.IElementType;
import org.intellij.markdown.lexer.MarkdownLexer;
import org.intellij.plugins.markdown.lang.MarkdownElementType;
import org.intellij.plugins.markdown.lang.parser.MarkdownParserManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MarkdownLexerAdapter extends LexerBase {
  @NotNull
  private final MarkdownLexer delegateLexer = MarkdownParserManager.FLAVOUR.createInlinesLexer();

  private int endOffset;

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int initialState) {
    this.endOffset = endOffset;
    delegateLexer.start(buffer, startOffset, endOffset);
  }

  @Override
  public int getState() {
    return 1;
  }

  @Nullable
  @Override
  public IElementType getTokenType() {
    return MarkdownElementType.platformType(delegateLexer.getType());
  }

  @Override
  public int getTokenStart() {
    return delegateLexer.getTokenStart();
  }

  @Override
  public int getTokenEnd() {
    return delegateLexer.getTokenEnd();
  }

  @Override
  public void advance() {
    delegateLexer.advance();
  }

  @NotNull
  @Override
  public CharSequence getBufferSequence() {
    return delegateLexer.getOriginalText();
  }

  @Override
  public int getBufferEnd() {
    return endOffset;
  }
}
