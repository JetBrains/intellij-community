package foo.bar.baz;

import java.util.*;

class Test {
  void test() {
    String s = Test.class.get<caret>Name();
  }
}