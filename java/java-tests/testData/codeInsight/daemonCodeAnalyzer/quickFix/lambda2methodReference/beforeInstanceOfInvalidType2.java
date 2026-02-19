// "Replace lambda with method reference" "false"
class X {
  static class A<X> {
    class B {}
  }

  void test() {
    Predicate<Object> pred = o -> o <caret>instanceof A<?>.B;
  }
}