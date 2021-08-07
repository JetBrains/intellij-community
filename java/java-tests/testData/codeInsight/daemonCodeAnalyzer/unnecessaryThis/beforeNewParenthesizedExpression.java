// "Remove unnecessary 'this' qualifier" "true"
class Main {
  class Nested {
  }
  void test() {
    Nested nested = (Main.th<caret>is).new Nested();
  }
}