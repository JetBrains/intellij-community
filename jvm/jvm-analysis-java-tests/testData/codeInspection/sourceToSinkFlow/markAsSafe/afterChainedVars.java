// "Mark 's2' as requiring validation" "true"
package org.checkerframework.checker.tainting.qual;

class Simple {

    void simple() {
      String s = foo();
      String s1 = s;
      String s2 = s1;
      sink(s2);
    }

    @Untainted String foo() {
      return "foo";
    }

    void sink(@Untainted String s1) {}

}