// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;

import static com.intellij.diff.util.DiffUtil.getLineCount;

@ApiStatus.Internal
public class AllLinesIterator implements Iterator<Pair<Integer, CharSequence>> {
  private final @NotNull Document myDocument;
  private int myLine = 0;

  public AllLinesIterator(@NotNull Document document) {
    myDocument = document;
  }

  @Override
  public boolean hasNext() {
    return myLine < getLineCount(myDocument);
  }

  @Override
  public Pair<Integer, CharSequence> next() {
    int offset1 = myDocument.getLineStartOffset(myLine);
    int offset2 = myDocument.getLineEndOffset(myLine);

    CharSequence text = myDocument.getImmutableCharSequence().subSequence(offset1, offset2);

    Pair<Integer, CharSequence> pair = new Pair<>(myLine, text);
    myLine++;

    return pair;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
