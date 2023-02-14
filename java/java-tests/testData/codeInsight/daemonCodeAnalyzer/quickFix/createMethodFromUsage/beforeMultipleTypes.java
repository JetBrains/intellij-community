// "Create method 'bar'" "true-preview"
class A {
    public void foo() {
      Object x = <caret>bar();
      String s = bar();
    }
}