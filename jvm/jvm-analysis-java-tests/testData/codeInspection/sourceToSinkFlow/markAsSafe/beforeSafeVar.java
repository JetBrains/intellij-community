// "Mark 's' as requiring validation" "false"
package org.checkerframework.checker.tainting.qual;

class Simple {

    void simple() {
      String s1 = foo();
      String s = foo();
      s = "test";
      s = s1;
      sink(<caret>s);
    }

    @Untainted String foo() {
      return "foo";
    }

    void sink(@Untainted String s1) {}

}