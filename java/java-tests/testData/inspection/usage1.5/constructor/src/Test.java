public class Test {
  void foo() {
    throw new IllegalArgumentException ("", new RuntimeException());
  }
}