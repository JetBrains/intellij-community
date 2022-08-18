// "Add 'type='" "true-preview"
class T {
  @interface A {
    String name();
    String type();
  }

  @A(type = "a", name = "b")
  void foo() {
  }
}