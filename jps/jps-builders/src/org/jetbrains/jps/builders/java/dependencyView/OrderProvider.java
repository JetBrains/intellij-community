/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.jps.builders.java.dependencyView;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * @author: db
 * To change this template use File | Settings | File Templates.
 */
class OrderProvider {
  private class Entry implements Comparable<Entry> {
    final String myString;
    final int myInt;

    private Entry(int anInt) {
      myInt = anInt;
      myString = myContext.getValue(anInt);
    }

    @Override
    public int compareTo(final Entry o) {
      return o.myString.compareTo(myString);
    }
  }
  private final DependencyContext myContext;
  private final List<Entry> myList = new LinkedList<>();

  OrderProvider(final DependencyContext context) {
      myContext = context;
  }

  void register (final int key) {
    myList.add(new Entry(key));
  }

  int[] get() {
    Collections.sort(myList);

    final int[] result = new int[myList.size()];
    int i = 0;

    for (final Entry e : myList) {
      result[i++] = e.myInt;
    }

    return result;
  }
}
