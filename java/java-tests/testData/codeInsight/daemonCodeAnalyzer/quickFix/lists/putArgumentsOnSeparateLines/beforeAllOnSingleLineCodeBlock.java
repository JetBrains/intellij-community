// "Put arguments on separate lines" "false"

class A {
  void foo(int a1, int a2, Runnable r) {
    foo(1, 2, () -> {
      <caret>
    })
  }
}