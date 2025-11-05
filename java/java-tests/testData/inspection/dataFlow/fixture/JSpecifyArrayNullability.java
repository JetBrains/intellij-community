package org.example;

import org.jspecify.annotations.*;

class UseArrayTest {

  @NullMarked
  abstract static class Container {
    interface Lib<T extends @Nullable Object> {
      T get();
    }

    abstract Lib<String[][]> useArray1();

    abstract Lib<? extends String[][]> useArray2();
  }


  @NullMarked
  abstract class Container2<L> {
    interface Lib2<T> {
      T get();
    }

    abstract Lib2<L> useArray2();
  }

  class ArrayUseTest {
    void test(Container a, Container2<? extends String[][]> b) {
      if (<warning descr="Condition 'a.useArray1().get() == null' is always 'false'">a.useArray1().get() == null</warning>) { //expected
        System.out.println("1");
      }

      String[][] strings2 = a.useArray2().get();
      if (<warning descr="Condition 'strings2 == null' is always 'false'">strings2 == null</warning>) { //expected
        System.out.println("1");
      }
      String[][] string3 = b.useArray2().get();
      if (<warning descr="Condition 'string3 == null' is always 'false'">string3 == null</warning>) { //expected
        System.out.println("1");
      }
    }
  }
}