package com.siyeh.igfixes.migration.try_finally_can_be_try_with_resources;

import java.io.*;

class MyAutoCloseable implements AutoCloseable {

  void foo() {
    System.out.println("foo");
  }

  @Override
  public void close() {
    System.out.println("close");
  }
}

class Java9 {

  public static void main(String[] args) throws FileNotFoundException {
    test(new MyAutoCloseable());
  }

  static void test(MyAutoCloseable m) throws FileNotFoundException {
      try (m; MyAutoCloseable m1 = new MyAutoCloseable()) {
          m.foo();
          m.foo();
      }
  }
}