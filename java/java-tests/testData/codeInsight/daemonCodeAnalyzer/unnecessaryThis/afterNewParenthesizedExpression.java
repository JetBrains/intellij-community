// "Remove unnecessary 'this' qualifier" "true"
class Main {
  class Nested {
  }
  void test() {
    Nested nested = new Nested();
  }
}