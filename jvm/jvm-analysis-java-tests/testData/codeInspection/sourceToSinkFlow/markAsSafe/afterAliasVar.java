// "Mark 'alias' as requiring validation" "true"
package org.checkerframework.checker.tainting.qual;

class Simple {

    void simple() {
      String s = foo();
      String alias = s;
      sink(alias);
    }

    @Untainted String foo() {
      return "foo";
    }

    void sink(@Untainted String s1) {}

}