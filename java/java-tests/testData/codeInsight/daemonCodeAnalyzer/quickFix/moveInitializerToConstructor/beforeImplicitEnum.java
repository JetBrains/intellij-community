// "Move initializer to constructor" "true"
enum C {
  foo;
  private final String myExtension = n<caret>ull;

  C() {
  }
}