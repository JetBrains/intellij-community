// "Create missing branches 'B' and 'C'" "true"
package com.siyeh.ipp.enumswitch;

class BeforeDefault {
  enum X {A, B, C}
  
  String test(X x) {
    return switch (x) {
      case A: yield "foo";
        case B:
            yield null;
        case C:
            yield null;
        default: yield "bar";
    };
  }
}