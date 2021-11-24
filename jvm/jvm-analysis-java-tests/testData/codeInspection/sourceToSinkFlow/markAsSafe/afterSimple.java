// "Mark 's' as requiring validation" "true"
package org.checkerframework.checker.tainting.qual;
 
class Simple {

    void simple() {
      String s = foo();
      sink(s);
    }

    @Untainted String foo() {
      return "foo";
    }

    void sink(@Untainted String s1) {}
}