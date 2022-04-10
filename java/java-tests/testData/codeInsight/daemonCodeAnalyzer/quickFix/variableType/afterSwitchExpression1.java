// "Change variable 'i' type to 'int'" "true"

class Test {
  void foo(int num) {
    int i = (((switch (num) {
      default -> 42;
    })));
  }
}