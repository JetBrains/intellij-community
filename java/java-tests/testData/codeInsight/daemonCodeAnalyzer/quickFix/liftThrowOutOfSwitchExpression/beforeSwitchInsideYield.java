// "Lift 'throw' out of 'switch' expression" "true-preview"

class Foo {
  void bar(int param) {
      int x = switch (param) {
          case 1 -> 7;
              default -> {
                  yield <caret>switch (param) {
                      default -> throw new RuntimeException("default");
                  };
              }
      };
  }
}