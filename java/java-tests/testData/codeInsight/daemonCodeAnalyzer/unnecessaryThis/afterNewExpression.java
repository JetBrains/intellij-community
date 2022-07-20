// "Remove unnecessary 'this' qualifier" "true-preview"
class Main {
  class Nested {
  }
  void test() {
    Nested nested = new Nested();
  }
}