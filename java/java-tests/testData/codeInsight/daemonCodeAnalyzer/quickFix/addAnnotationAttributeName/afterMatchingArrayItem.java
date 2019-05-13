// "Add 'type='" "true"
class T {
  @interface A {
    String[] type();
  }

  @A(type = "t")
  void foo() {
  }
}