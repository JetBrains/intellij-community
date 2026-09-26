// "Create missing branches 'E1' and 'E2'" "true"
class Foo {
  void foo(E e) {
    switch (e)<caret>
  }
}

enum E {
  E1, E2;
}