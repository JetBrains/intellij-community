class Foo {
  void foo(Constants c) {
    switch (c) {
        case BAR0:<caret>
    }
  }
}

enum Constants {
  BAR0, BAR1;
}