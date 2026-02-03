sealed class Parent {

  void test() {
    Object obj = new A<caret>();
  }

  static final class A extends Parent {
    void foo() {}
  }

}