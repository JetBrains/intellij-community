public class A {
  static void f() {
    A a = new A().lo<caret>;
  }

  A logMethod() {
    return null;
  }

  A anotherLogMethod() {
    return null;
  }
}
