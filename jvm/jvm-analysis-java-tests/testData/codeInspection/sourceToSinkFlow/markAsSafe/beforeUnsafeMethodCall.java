// "Mark 'source' as requiring validation" "false"
package org.checkerframework.checker.tainting.qual;

class Simple {

    void simple() {
      sink(<caret>source());
    }

    String foo() {
      return "foo";
    }
  
    @Tainted String source() {
      return "source";
    }

    void sink(@Untainted String s1) {}

}