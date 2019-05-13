public class Test<T extends Number> {
  String t;
  void foo() {
    int i = t.intValue();
  }
}
