// "Lift 'throw' out of 'switch' expression" "false"

class Foo {
  int bar = <caret>switch(param) { default -> throw new RuntimeException(); };
}