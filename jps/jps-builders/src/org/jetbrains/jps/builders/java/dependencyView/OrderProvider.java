package org.jetbrains.jps.builders.java.dependencyView;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * @author: db
 * Date: 24.06.12
 * Time: 19:08
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
  private final List<Entry> myList = new LinkedList<Entry>();

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
