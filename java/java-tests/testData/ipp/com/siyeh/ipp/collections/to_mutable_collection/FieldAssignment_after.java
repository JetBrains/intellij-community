import java.util.*;

class Test {
  private Set<String> foo;

  void test(Test t1, String s) {
    t1.foo = new HashSet<>();
      t1.foo.add(s);
  }
}