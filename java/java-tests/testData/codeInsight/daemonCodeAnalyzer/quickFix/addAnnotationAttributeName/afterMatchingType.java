// "Add 'name='" "true-preview"
class T {
  @interface A {
    int size();
    String name();
  }

  @A(name = "a")
  void foo() {
  }
}