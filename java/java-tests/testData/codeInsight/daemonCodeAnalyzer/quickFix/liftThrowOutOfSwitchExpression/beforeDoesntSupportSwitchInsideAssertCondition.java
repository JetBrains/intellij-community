// "Lift 'throw' out of 'switch' expression" "false"

class Foo {
  void bar(int param) {
      assert <caret>switch(param) { default -> throw new RuntimeException(); } : "message";
  }
}