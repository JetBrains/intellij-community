// "Fix all 'Enum 'switch' statement that misses case' problems in file" "false"
package com.siyeh.ipp.enumswitch;

class Main {
  enum X {A, B, C, D, E, F}
  
  void test(X x) {
    switch (x)<caret> {
      case A -> System.out.println("foo");
      case C, D -> System.out.println("bar");
      case F, null -> System.out.println("baz");
    };
  }
}