// "Create Method 'bar'" "true"
class A {
    public void foo() {
      Object x = <caret>bar();
      String s = bar();
    }
}