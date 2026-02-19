// "Add explicit type arguments" "true-preview"

import java.util.*;

class MyTest {

  void foo(List<String> list1, List<String> list2) {}

  void bar() {
   foo(Collections.<String>singletonList("x"), unresolved());
  }
}