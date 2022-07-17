sealed class Parent permits Parent.A {

  void test() {
    Object obj = new A<caret>();
  }

  static final class A extends Parent {}

}