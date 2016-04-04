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
package com.intellij.openapi.editor.ex;

import com.intellij.util.containers.PeekableIterator;

import java.util.NoSuchElementException;

/**
 * An iterator you must to {@link #dispose()} after use
 */
public interface MarkupIterator<T> extends PeekableIterator<T> {
  void dispose();

  MarkupIterator EMPTY = new MarkupIterator() {
    @Override
    public void dispose() {
    }

    @Override
    public Object peek() {
      return null;
    }

    @Override
    public boolean hasNext() {
      return false;
    }

    @Override
    public Object next() {
      throw new NoSuchElementException();
    }

    @Override
    public void remove() {
      throw new NoSuchElementException();
    }
  };
}
