// "Add 'size='" "false"
class T {
  @interface A {
    int size();
  }

  @A(<caret>"a")
  void foo() {
  }
}