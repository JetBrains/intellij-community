// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.search;

import com.intellij.lexer.LexerBase;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.psi.tree.IElementType;
import com.intellij.util.text.CharSequenceSubSequence;
import org.jetbrains.annotations.NotNull;

public class LexerEditorHighlighterLexer extends LexerBase {
  private HighlighterIterator iterator;
  private CharSequence buffer;
  private int start;
  private int end;
  private final EditorHighlighter myHighlighter;
  private final boolean myAlreadyInitializedHighlighter;

  public LexerEditorHighlighterLexer(final EditorHighlighter highlighter, boolean alreadyInitializedHighlighter) {
    myHighlighter = highlighter;
    myAlreadyInitializedHighlighter = alreadyInitializedHighlighter;
  }

  @Override
  public void start(@NotNull CharSequence buffer, int startOffset, int endOffset, int state) {
    if (myAlreadyInitializedHighlighter) {
      this.buffer = buffer;
      start = startOffset;
      end = endOffset;
    } else {
      myHighlighter.setText(new CharSequenceSubSequence(this.buffer = buffer, start = startOffset, end = endOffset));
    }
    iterator = myHighlighter.createIterator(0);
  }

  public void resetPosition(int offset) {
    iterator = myHighlighter.createIterator(offset);
  }

  @Override
  public int getState() {
    return 0;
  }

  @Override
  public IElementType getTokenType() {
    if (iterator.atEnd()) return null;
    return iterator.getTokenType();
  }

  @Override
  public int getTokenStart() {
    return iterator.getStart();
  }

  @Override
  public int getTokenEnd() {
    return iterator.getEnd();
  }

  @Override
  public void advance() {
    iterator.advance();
  }

  @Override
  public @NotNull CharSequence getBufferSequence() {
    return buffer;
  }

  @Override
  public int getBufferEnd() {
    return end;
  }

  public HighlighterIterator getHighlighterIterator() {
    return iterator;
  }
}
