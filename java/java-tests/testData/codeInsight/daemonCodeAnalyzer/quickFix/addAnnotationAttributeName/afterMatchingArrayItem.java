// "Add 'type='" "true-preview"
class T {
  @interface A {
    String[] type();
  }

  @A(type = "t")
  void foo() {
  }
}