// "Lift 'throw' out of 'switch' expression" "false"

class Foo {
  void bar(int param) {
      for (int i = <caret>switch(param) { default -> throw new RuntimeException(); }; i < 0; i++) {
      }
  }
}