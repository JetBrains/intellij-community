package org.example;

import org.jspecify.annotations.NullMarked;

class SimpleArrayTest {
  static class Container {
    @NullMarked
    abstract static class ArraySameType {
      interface Lib<T> {
        T get();
      }

      abstract void useArray(Lib<Object[]> l); //not expected

      abstract Lib<? extends String[][]> useArray2(); //not expected
    }
  }
}