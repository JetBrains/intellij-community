// "Mark 'foo' as requiring validation" "true"
package org.checkerframework.checker.tainting.qual;

class Simple {

    void simple() {
      sink(<caret>foo());
    }

    String foo() {
      return "foo";
    }

    void sink(@Untainted String s1) {}

}