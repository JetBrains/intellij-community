import java.util.Arrays;

// "Collapse into loop" "true"
class X {
  void test() {
      for (String s : Arrays.asList("foo", "bar")) {
          foo(s);
      }
      foo(123);
  }
  
  void foo(Object obj) {}
}