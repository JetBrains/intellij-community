public class B {
  void foo() {
    A a = bar();
    if (a instanceof I) {
    }
  }

  A bar() {return null;}
}