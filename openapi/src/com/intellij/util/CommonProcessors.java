package com.intellij.util;

import java.util.ArrayList;
import java.util.Collection;

/**
 * Created by IntelliJ IDEA.
 * User: max
 * Date: Dec 21, 2004
 * Time: 6:52:46 PM
 * To change this template use File | Settings | File Templates.
 */
public class CommonProcessors {
  public static class CollectProcessor<T> implements Processor<T> {
    private final Collection<T> myCollection;

    public CollectProcessor(Collection<T> collection) {
      myCollection = collection;
    }

    public CollectProcessor() {
      myCollection = new ArrayList<T>();
    }

    public boolean process(T t) {
      myCollection.add(t);
      return true;
    }

    public <T> T[] toArray(T[] a) {
      return myCollection.toArray(a);
    }
  }

  public static class FindFirstProcessor<T> implements Processor<T> {
    private T myValue;

    public boolean isFound() {
      return myValue != null;
    }

    public T getFoundValue() {
      return myValue;
    }

    public boolean process(T t) {
      myValue = t;
      return false;
    }
  }
}
