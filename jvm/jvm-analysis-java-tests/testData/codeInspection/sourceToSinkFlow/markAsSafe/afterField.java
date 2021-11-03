// "Mark 's2' as safe" "true"
package org.checkerframework.checker.tainting.qual;

class Simple {

    @Untainted
    private String s = foo();

    void simple() {
      String s2 = s;
      sink(s2);
    }

    String foo() {
      return "foo";
    }

    void sink(@Untainted String s1) {}

}