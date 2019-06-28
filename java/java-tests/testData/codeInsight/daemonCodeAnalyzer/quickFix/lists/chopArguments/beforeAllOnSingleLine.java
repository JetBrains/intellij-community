// "Put arguments on separate lines" "true"

class A {
  void foo(int a1, int a2, int a3) {
    foo(1, 2<caret>, 3)
  }
}