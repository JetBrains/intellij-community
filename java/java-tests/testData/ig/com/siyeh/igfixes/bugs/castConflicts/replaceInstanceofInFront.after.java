public class Test {
  void foo(Object o) {
    if (o instanceof Number) {
      assert o instanceof Integer;
      Integer i = (Integer)o;
    }
  }
}