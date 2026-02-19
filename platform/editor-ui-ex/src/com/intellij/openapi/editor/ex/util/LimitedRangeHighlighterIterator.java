// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.editor.ex.util;

import com.intellij.openapi.editor.highlighter.HighlighterIterator;

public class LimitedRangeHighlighterIterator extends HighlighterIteratorWrapper {
  private final int myStartOffset;
  private final int myEndOffset;


  LimitedRangeHighlighterIterator(final HighlighterIterator original, final int startOffset, final int endOffset) {
    super(original);
    myStartOffset = startOffset;
    myEndOffset = endOffset;
  }

  @Override
  public int getStart() {
    return Math.max(super.getStart(), myStartOffset);
  }

  @Override
  public int getEnd() {
    return Math.min(super.getEnd(), myEndOffset);
  }

  @Override
  public boolean atEnd() {
    return super.atEnd() || super.getStart() >= myEndOffset || super.getEnd() <= myStartOffset;
  }

}
