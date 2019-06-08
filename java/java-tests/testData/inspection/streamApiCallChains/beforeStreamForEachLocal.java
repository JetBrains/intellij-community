// "Replace 'stream().forEach()' with 'forEach()' (may change semantics)" "true"

import java.util.ArrayList;

class Test {
  void foo() {
    class X extends ArrayList<String> {
      class Y {
        {
          stream().fo<caret>rEach(System.out::println);
        }
        
        void forEach(Object obj) {}
      }
    }
  }
}