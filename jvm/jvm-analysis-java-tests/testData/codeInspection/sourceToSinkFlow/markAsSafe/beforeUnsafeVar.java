// "Mark 's2' as requiring validation" "false"
package org.checkerframework.checker.tainting.qual;

class Simple {

    void simple() {
      String s1 = source();
      String s2 = s1;
      s2 = foo();
      sink(<caret>s2);
    }

    String foo() {
      return "foo";
    }
  
    @Tainted String source() {
      return "source";
    }

    void sink(@Untainted String s1) {}

}