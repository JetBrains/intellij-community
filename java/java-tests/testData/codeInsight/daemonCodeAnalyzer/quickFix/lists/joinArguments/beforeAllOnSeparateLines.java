// "Put arguments on one line" "true"

class A {
  void foo(int a1, int a2, int a3) {
    foo(
      12,
      23,<caret>
        4
    );
  }
}