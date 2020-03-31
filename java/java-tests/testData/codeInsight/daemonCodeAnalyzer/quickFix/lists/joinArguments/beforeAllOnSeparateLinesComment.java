// "Put arguments on one line" "false"

class A {
  void foo(int a1, int a2, int a3) {
    foo(
      12, // 12
      23,<caret>
        4
    );
  }
}