// "Lift 'throw' out of 'switch' expression" "false"

class Foo {
  void bar(int param) {
      try (java.io.InputStream resource = <caret>switch(param) { default -> throw new RuntimeException(); }) {
      }
  }
}