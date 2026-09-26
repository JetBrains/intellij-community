// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
      for (int i = 0; i < 10; i++) {
          throw <caret>switch (param) {
              default -> new RuntimeException("default");
          };
      }
  }
}