// "Change variable 'i' type to 'int'" "true"

class Test {
  void foo(int num) {
    String i = (((switch (num) {
      default -> 42<caret>;
    })));
  }
}