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

package com.intellij.history.utils;

import java.util.Iterator;
import java.util.List;

// todo move to com.intellij.util
public class Reversed<T> implements Iterable<T> {
  private final List<T> myList;

  public static <T> Reversed<T> list(List<T> l) {
    return new Reversed<T>(l);
  }

  private Reversed(List<T> l) {
    myList = l;
  }

  public Iterator<T> iterator() {
    return new Iterator<T>() {
      int i = myList.size() - 1;

      public boolean hasNext() {
        return i >= 0;
      }

      public T next() {
        return myList.get(i--);
      }

      public void remove() {
        throw new UnsupportedOperationException();
      }
    };
  }
}
