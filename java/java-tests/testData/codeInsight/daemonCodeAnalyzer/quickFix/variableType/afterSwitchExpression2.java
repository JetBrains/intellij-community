// "Change variable 'i' type to 'int'" "true-preview"

class Test {
  void foo(int num) {
    int i;
    i = (((switch (num) {
      default -> 42;
    })));
  }
}