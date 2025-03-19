// "Create missing branches 'B' and 'E'" "true"
package com.siyeh.ipp.enumswitch;

class BeforeDefault {
  enum X {A, B, C, D, E, F}
  
  void test(X x) {
    switch (x)<caret> {
      case A -> System.out.println("foo");
      case C, D -> System.out.println("foo");
      case F -> System.out.println("foo");
    }
  }
}