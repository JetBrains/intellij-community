// "Lift 'throw' out of 'switch' expression" "false"


class Foo {
  void bar() {
      class Local {
          int bar = <caret>switch(3) { default -> throw new RuntimeException(); };
      }
  }
}