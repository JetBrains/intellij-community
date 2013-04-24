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

public class StoppableLexerAdapter extends DelegateLexer {

  public interface StoppingCondition {
    boolean stopsAt(IElementType token, int start, int end);
  }

  private final StoppingCondition myCondition;
  private boolean myStopped = false;

  public StoppableLexerAdapter(final StoppingCondition condition, final Lexer original) {
    super(original);
    myCondition = condition;
    myStopped = myCondition.stopsAt(original.getTokenType(), original.getTokenStart(), original.getTokenEnd());
  }

  @Override
  public void advance() {
    if (myStopped) return;
    super.advance();

    if (myCondition.stopsAt(getDelegate().getTokenType(), getDelegate().getTokenStart(), getDelegate().getTokenEnd())) {
      myStopped = true;
    }
  }

  public int getPrevTokenEnd() {
    Lexer delegate = getDelegate();
    return delegate instanceof StoppableLexerAdapter ? ((StoppableLexerAdapter)delegate).getPrevTokenEnd() : ((FilterLexer)delegate).getPrevTokenEnd();
  }

  @Override
  public int getTokenEnd() {
    return myStopped ? super.getTokenStart() : super.getTokenEnd();
  }

  @Override
  public IElementType getTokenType() {
    return myStopped ? null : super.getTokenType();
  }

  @Override
  public LexerPosition getCurrentPosition() {
    return getDelegate().getCurrentPosition();
  }

  @Override
  public void restore(LexerPosition position) {
    getDelegate().restore(position);
  }

}
