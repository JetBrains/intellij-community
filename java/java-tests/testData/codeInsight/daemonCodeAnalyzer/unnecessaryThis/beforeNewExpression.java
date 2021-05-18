// "Remove unnecessary 'this' qualifier" "true"
class Main {
  class Nested {
  }
  void test() {
    Nested nested = th<caret>is.new Nested();
  }
}