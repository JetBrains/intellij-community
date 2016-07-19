// "Add 'type='" "true"
class T {
  @interface A {
    String[] type();
  }

  @A(<caret>"t")
  void foo() {
  }
}