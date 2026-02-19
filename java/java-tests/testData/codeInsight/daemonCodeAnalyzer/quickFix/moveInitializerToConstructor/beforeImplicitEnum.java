// "Move initializer to constructor" "true-preview"
enum C {
  foo;
  private final String myExtension = n<caret>ull;

  C() {
  }
}