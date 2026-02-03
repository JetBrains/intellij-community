public class Test {
  void foo(Object o) {
    if (o instanceof Number) {
      assert o instanceof String;
      Integer i = (Integer)<caret>o;
    }
  }
}