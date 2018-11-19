import org.jetbrains.annotations.*;

import java.util.*;

class Test {
   void foo() {
     String[] data = new String[] {"abs", "def"};
     for (@NotNull String foo: data) {
       assert <warning descr="Condition 'foo != null' is always 'true'">foo != null</warning>; // Condition always true
     }
   }
}