// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/*
 * @author max
 */
package com.intellij.lexer;

import com.intellij.psi.tree.IElementType;
import org.jetbrains.annotations.NotNull;

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
  public @NotNull LexerPosition getCurrentPosition() {
    return getDelegate().getCurrentPosition();
  }

  @Override
  public void restore(@NotNull LexerPosition position) {
    getDelegate().restore(position);
  }

}
