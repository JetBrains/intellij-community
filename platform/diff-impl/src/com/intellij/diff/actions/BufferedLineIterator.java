// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.actions;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

@ApiStatus.Internal
public abstract class BufferedLineIterator implements Iterator<Pair<Integer, CharSequence>> {
  private final @NotNull List<Pair<Integer, CharSequence>> myBuffer;

  public BufferedLineIterator() {
    myBuffer = new LinkedList<>();
  }

  public abstract boolean hasNextBlock();

  public abstract void loadNextBlock();

  protected void init() {
    while (myBuffer.isEmpty() && hasNextBlock()) {
      loadNextBlock();
    }
  }

  protected void addLine(int line, @NotNull CharSequence text) {
    myBuffer.add(Pair.create(line, text));
  }

  @Override
  public boolean hasNext() {
    return !myBuffer.isEmpty();
  }

  @Override
  public Pair<Integer, CharSequence> next() {
    Pair<Integer, CharSequence> result = myBuffer.remove(0);

    while (myBuffer.isEmpty() && hasNextBlock()) {
      loadNextBlock();
    }

    return result;
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException();
  }
}
