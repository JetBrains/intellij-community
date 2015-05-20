// "Create method 'bar'" "true"
class A {
    public void foo() {
      Object x = <caret>bar();
      String s = bar();
    }
}