class Test {
}

class Test2 extends Test {
  void foo(final boolean d) {
      if (d) {
        foo(false);
      }
      System.out.println();

  }
}
