// "Change variable 'i' type to 'int'" "true"

class Test {
  void foo(int num) {
    String i;
    i = (((switch (num) {
      default -> 42<caret>;
    })));
  }
}