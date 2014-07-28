public class Test<T extends Number> {
  Integer t;
  void foo() {
    int i = t.intValue();
  }
}
