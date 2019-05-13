// "Add 'type='" "true"
class T {
  @interface A {
    String name();
    String type();
  }

  @A(<caret>"a", name = "b")
  void foo() {
  }
}