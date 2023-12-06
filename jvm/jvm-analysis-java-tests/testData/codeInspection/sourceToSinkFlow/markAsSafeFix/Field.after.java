package org.checkerframework.checker.tainting.qual;

class Simple {

    private @Untainted String s = foo();

    void simple() {
      String s2 = s;
      sink(s2);
    }

    @Untainted
    String foo() {
      return "foo";
    }

    void sink(@Untainted String s1) {}

}