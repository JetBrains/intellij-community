class Foo {
  void foo(Constants c) {
    switch (c) {
        case B<caret>
    }
  }
}

enum Constants {
  BAR0, BAR1;
}