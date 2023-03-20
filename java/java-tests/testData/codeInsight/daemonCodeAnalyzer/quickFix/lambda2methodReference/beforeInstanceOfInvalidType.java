// "Replace lambda with method reference" "false"
class X {
  void test() {
    Predicate<Object> predicate = c -> c <caret>instanceof String.;
  }
}