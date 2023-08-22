// "Move initializer to constructor" "true-preview"
enum C {
  foo;
  private final String myExtension;

  C() {
      myExtension = null;
  }
}