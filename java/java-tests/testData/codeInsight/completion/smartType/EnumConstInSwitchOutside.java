enum E {
  CONS
}

class Foo {
    void foo (E e) {
    switch (e) {
      case C<caret>
    }
  }
}
