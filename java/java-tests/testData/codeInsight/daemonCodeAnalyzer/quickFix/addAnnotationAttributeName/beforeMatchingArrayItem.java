// "Add 'type='" "true-preview"
class T {
  @interface A {
    String[] type();
  }

  @A(<caret>"t")
  void foo() {
  }
}