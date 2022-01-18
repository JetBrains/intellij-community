// "Mark 's2' as requiring validation" "true"
package org.checkerframework.checker.tainting.qual;

class Simple {

    void simple() {
      String s1 = other();
      String s2 = s1;
      s2 = foo();
      sink(s2);
    }

    @Untainted String foo() {
      return "foo";
    }

    @Untainted String other() {
      return "other";
    }

    void sink(@Untainted String s1) {}

}