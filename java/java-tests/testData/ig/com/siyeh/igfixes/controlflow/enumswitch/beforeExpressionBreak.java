// "Create missing branches 'B' and 'C'" "true"
package com.siyeh.ipp.enumswitch;

class BeforeDefault {
  enum X {A, B, C}
  
  String test(X x) {
    return <caret>switch (x) {
      case A: yield "foo";
      default: yield "bar";
    };
  }
}