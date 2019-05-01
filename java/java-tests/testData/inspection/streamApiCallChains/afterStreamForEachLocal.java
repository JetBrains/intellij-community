// "Replace 'stream().forEach()' with 'forEach()' (may change semantics)" "true"

import java.util.ArrayList;

class Test {
  void foo() {
    class X extends ArrayList<String> {
      class Y {
        {
          X.this.forEach(System.out::println);
        }
        
        void forEach(Object obj) {}
      }
    }
  }
}