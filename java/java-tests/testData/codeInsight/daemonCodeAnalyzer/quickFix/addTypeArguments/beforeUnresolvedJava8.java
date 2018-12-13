// "Add explicit type arguments" "true"

import java.util.*;

class MyTest {

  void foo(List<String> list1, List<String> list2) {}

  void bar() {
   foo(Collections.single<caret>tonList("x"), unresolved());
  }
}