package com.siyeh.ipp.forloop.iterator;

import java.util.Iterator;

class GenericTypes<K, V> implements Iterable<V> {

  @Override
  public Iterator<V> iterator() {
    return null;
  }

  public void test() {
    final GenericTypes<String, Integer> test = new GenericTypes<String, Integer>();
    <caret>for (Integer integer : test) {

    }
  }
}
