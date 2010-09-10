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

/*
 * @author max
 */
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.Nullable;

public class DelegateLexer extends LexerBase {
  private final Lexer myDelegate;

  public DelegateLexer(Lexer delegate) {
    myDelegate = delegate;
  }

  public final Lexer getDelegate() {
    return myDelegate;
  }

  public void start(CharSequence buffer, int startOffset, int endOffset, int initialState) {
    myDelegate.start(buffer, startOffset, endOffset, initialState);
  }

  public int getState() {
    return myDelegate.getState();
  }

  @Nullable
  public IElementType getTokenType() {
    return myDelegate.getTokenType();
  }

  public int getTokenStart() {
    return myDelegate.getTokenStart();
  }

  public int getTokenEnd() {
    return myDelegate.getTokenEnd();
  }

  public void advance() {
    myDelegate.advance();
  }

  public final CharSequence getBufferSequence() {
    return myDelegate.getBufferSequence();
  }

  public int getBufferEnd() {
    return myDelegate.getBufferEnd();
  }
}
