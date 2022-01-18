// "Mark 's2' as requiring validation" "true"
package org.checkerframework.checker.tainting.qual;

class Simple {

    private String s = foo();

    void simple() {
      String s2 = s;
      sink(<caret>s2);
    }

    String foo() {
      return "foo";
    }

    void sink(@Untainted String s1) {}

}