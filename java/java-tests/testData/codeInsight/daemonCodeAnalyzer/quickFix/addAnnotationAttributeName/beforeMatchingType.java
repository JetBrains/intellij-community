// "Add 'name='" "true"
class T {
  @interface A {
    int size();
    String name();
  }

  @A(<caret>"a")
  void foo() {
  }
}