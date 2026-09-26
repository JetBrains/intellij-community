// "Replace 'stream().forEach()' with 'forEach()' (may change semantics)" "false"

import java.util.ArrayList;

class Test {
  void foo() {
    new ArrayList<String>() {
      class Y {
        {
          // Impossible to qualify forEach() call
          stream().fo<caret>rEach(System.out::println);
        }
        
        void forEach(Object obj) {}
      }
    }
  }
}