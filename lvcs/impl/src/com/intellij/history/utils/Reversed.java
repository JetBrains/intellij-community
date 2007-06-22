package com.intellij.history.utils;

import java.util.Iterator;
import java.util.List;

// todo move to com.intellij.util
public class Reversed<T> implements Iterable<T> {
  private List<T> myList;

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
