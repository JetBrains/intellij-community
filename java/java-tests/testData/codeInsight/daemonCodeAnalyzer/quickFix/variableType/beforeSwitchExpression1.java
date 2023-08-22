// "Change variable 'i' type to 'int'" "true-preview"

class Test {
  void foo(int num) {
    String i = (((switch (num) {
      default -> 42<caret>;
    })));
  }
}