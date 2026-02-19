// "Create missing branches 'B' and 'E'" "true"
package com.siyeh.ipp.enumswitch;

class BeforeDefault {
  enum X {A, B, C, D, E, F}
  
  void test(X x) {
    switch (x) {
      case A -> System.out.println("foo");
        case B -> {
        }
        case C, D -> System.out.println("foo");
        case E -> {
        }
        case F -> System.out.println("foo");
    }
  }
}