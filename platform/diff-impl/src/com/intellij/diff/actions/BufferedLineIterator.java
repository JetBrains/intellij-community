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
package com.intellij.diff.actions;

import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public abstract class BufferedLineIterator implements Iterator<Pair<Integer, CharSequence>> {
  @NotNull private final List<Pair<Integer, CharSequence>> myBuffer;

  public BufferedLineIterator() {
    myBuffer = new LinkedList<Pair<Integer, CharSequence>>();
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
