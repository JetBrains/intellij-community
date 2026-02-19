// "Create missing branch 'E2'" "true"
class Foo {
  void foo(E e) {
    if (e != E.E1) {
      switch (e)<caret>
    }
  }
}

enum E {
  E1, E2;
}