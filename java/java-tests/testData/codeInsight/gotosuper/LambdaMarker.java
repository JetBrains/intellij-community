interface I {
  void run();
}

class Foo {
  {
    I i = <caret>() -> {};
  }
}