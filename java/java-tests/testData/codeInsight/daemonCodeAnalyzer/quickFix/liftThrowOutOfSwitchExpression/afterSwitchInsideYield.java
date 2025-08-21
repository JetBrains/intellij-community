// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
      int x = switch (param) {
          case 1 -> 7;
              default -> {
                  throw <caret>switch (param) {
                      default -> new RuntimeException("default");
                  };
              }
      };
  }
}