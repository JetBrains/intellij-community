/*
 * Copyright 2000-2007 JetBrains s.r.o.
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
package com.intellij.lexer;

import com.intellij.util.text.CharArrayCharSequence;
import com.intellij.util.text.CharArrayUtil;

/**
 *
 */
public abstract class LexerBase implements Lexer{
  public LexerPosition getCurrentPosition() {
    final int offset = getTokenStart();
    final int intState = getState();
    return new LexerPositionImpl(offset, intState);
  }

  public void restore(LexerPosition position) {
    start(getBufferSequence(), position.getOffset(), getBufferEnd(), position.getState());
  }

  public CharSequence getBufferSequence() {
    return new CharArrayCharSequence(getBuffer());
  }

  public void start(char[] buffer) {
    start(buffer,0, buffer.length);
  }

  public void start(char[] buffer, int startOffset, int endOffset) {
    start(buffer,startOffset,endOffset,0);
  }

  public void start(final CharSequence buffer, final int startOffset, final int endOffset, final int initialState) {
    start(CharArrayUtil.fromSequence(buffer), startOffset, endOffset, initialState);
  }
}
