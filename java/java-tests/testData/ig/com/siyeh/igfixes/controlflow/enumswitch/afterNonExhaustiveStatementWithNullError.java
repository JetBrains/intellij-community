// "Create missing branches 'B' and 'E'" "true"
package com.siyeh.ipp.enumswitch;

class Main {
  enum X {A, B, C, D, E, F}
  
  void test(X x) {
    switch (x) {
      case A -> System.out.println("foo");
        case B -> {
        }
        case C, D -> System.out.println("bar");
        case E -> {
        }
        case F -> System.out.println("baz");
      case null -> System.out.println("baz");
    };
  }
}