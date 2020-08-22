// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
  private final class Entry implements Comparable<Entry> {
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
