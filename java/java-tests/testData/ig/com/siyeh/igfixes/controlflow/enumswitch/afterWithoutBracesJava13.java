// "Create missing branches 'E1' and 'E2'" "true"
class Foo {
  void foo(E e) {
      switch (e) {
          case E1 -> {
          }
          case E2 -> {
          }
      }
  }
}

enum E {
  E1, E2;
}