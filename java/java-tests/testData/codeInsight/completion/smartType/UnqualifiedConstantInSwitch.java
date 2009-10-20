enum Foo {
    FOO, BAR
  }


class Main {

  {
    Foo a;
    Foo b;
    switch (a) {
      case FOO: b = B<caret>
    }
  }

}