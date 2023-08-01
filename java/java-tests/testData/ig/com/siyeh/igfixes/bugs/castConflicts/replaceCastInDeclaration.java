public class Test {
  void foo(Object o) {
    if (o instanceof String) {
      Integer i = (Integer)<caret>o;
    }
  }
}