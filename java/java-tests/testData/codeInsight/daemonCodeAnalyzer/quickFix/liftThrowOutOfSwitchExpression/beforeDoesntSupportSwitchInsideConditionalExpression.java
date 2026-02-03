// "Lift 'throw' out of 'switch' expression" "false"

class Foo {
  int bar(int param, boolean b) {
      return b? <caret>switch (param) { default -> throw new RuntimeException("default"); } : 7;
  }
}