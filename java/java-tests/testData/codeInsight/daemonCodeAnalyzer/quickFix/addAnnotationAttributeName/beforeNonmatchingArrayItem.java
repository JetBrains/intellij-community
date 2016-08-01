// "Add 'type='" "false"
class T {
  @interface A {
    String[] type();
  }

  @A(<caret>42)
  void foo() {
  }
}